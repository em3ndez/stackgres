/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.cluster.controller;

import static io.stackgres.common.ConfigFilesUtil.configChanged;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.stackgres.cluster.common.ClusterControllerEventReason;
import io.stackgres.cluster.common.ClusterPatroniConfigEventReason;
import io.stackgres.cluster.common.PatroniCommandUtil;
import io.stackgres.cluster.common.PostgresUtil;
import io.stackgres.cluster.common.StackGresClusterContext;
import io.stackgres.common.ClusterContext;
import io.stackgres.common.ClusterPath;
import io.stackgres.common.EnvoyUtil;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgcluster.StackGresClusterPostgres;
import io.stackgres.common.crd.sgcluster.StackGresClusterSpec;
import io.stackgres.common.crd.sgcluster.StackGresClusterSsl;
import io.stackgres.common.postgres.PostgresConnectionManager;
import io.stackgres.common.resource.ResourceFinder;
import io.stackgres.operatorframework.reconciliation.ReconciliationResult;
import org.jooq.lambda.Unchecked;
import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@SuppressFBWarnings(value = "DMI_HARDCODED_ABSOLUTE_FILENAME",
    justification = "This is not a bug if working with containers")
public class PostgresSslReconciliator {

  private static final Logger LOGGER = LoggerFactory.getLogger(PostgresSslReconciliator.class);

  private final EventController eventController;
  private final ResourceFinder<Secret> secretFinder;
  private final PostgresConnectionManager postgresConnectionManager;

  @Dependent
  public static class Parameters {
    @Inject EventController eventController;
    @Inject ResourceFinder<Secret> secretFinder;
    @Inject PostgresConnectionManager postgresConnectionManager;
  }

  @Inject
  public PostgresSslReconciliator(Parameters parameters) {
    this.eventController = parameters.eventController;
    this.secretFinder = parameters.secretFinder;
    this.postgresConnectionManager = parameters.postgresConnectionManager;
  }

  public ReconciliationResult<Void> reconcile(KubernetesClient client,
      StackGresClusterContext context) {
    try {
      reconcilePostgresSsl(client, context);
      return new ReconciliationResult<>();
    } catch (IOException | RuntimeException ex) {
      LOGGER.error("An error occurred while reconciling postgres SSL", ex);
      try {
        eventController.sendEvent(ClusterControllerEventReason.CLUSTER_CONTROLLER_ERROR,
            "An error occurred while reconciling postgres SSL: " + ex.getMessage(),
            client);
      } catch (Exception eventEx) {
        LOGGER.error("An error occurred while sending an event", eventEx);
      }
      return new ReconciliationResult<>(ex);
    }
  }

  /**
   * <p>
   * If any file in /etc/ssl change or if ssl is not working will reload PostgreSQL config through
   *  patroni reload.
   * </p>
   */
  private void reconcilePostgresSsl(KubernetesClient client, StackGresClusterContext context)
      throws IOException {
    final StackGresCluster cluster = context.getCluster();

    if (sslChanged()
        || (Optional.of(cluster)
            .map(StackGresCluster::getSpec)
            .map(StackGresClusterSpec::getPostgres)
            .map(StackGresClusterPostgres::getSsl)
            .map(StackGresClusterSsl::getEnabled)
            .orElse(false)
            && !testPostgresSsl(context))) {
      copySsl();
      try {
        PatroniCommandUtil.reloadPatroniConfig();
      } catch (Exception ex) {
        LOGGER.warn("Was not able to reload Patroni, will try later if needed: " + ex.getMessage());
      }
      LOGGER.info("SSL config updated");
      eventController.sendEvent(ClusterPatroniConfigEventReason.CLUSTER_PATRONI_CONFIG_UPDATED,
          "SSL config updated", client);
    }
  }

  private boolean sslChanged() throws IOException {
    return transformSslFiles(list -> list
        .anyMatch(Unchecked.predicate(t -> configChanged(t.v1, t.v2))));
  }

  private void copySsl() throws IOException {
    forEachSslFile(list -> list
        .forEach(Unchecked.consumer(
            t -> {
              Files.copy(t.v1, t.v2,
                  StandardCopyOption.REPLACE_EXISTING);
              Files.setPosixFilePermissions(t.v2,
                  Set.of(
                      PosixFilePermission.OWNER_READ,
                      PosixFilePermission.OWNER_WRITE));
            })));
  }

  private void forEachSslFile(Consumer<Stream<Tuple2<Path, Path>>> consumer)
      throws IOException {
    transformSslFiles(list -> {
      consumer.accept(list);
      return null;
    });
  }

  private <T> T transformSslFiles(Function<Stream<Tuple2<Path, Path>>, T> transformer)
      throws IOException {
    try (var list = Files.list(Path.of(ClusterPath.SSL_PATH.path()))) {
      return transformer.apply(list
          .filter(Predicate.not(Files::isDirectory))
          .map(file -> Tuple.tuple(
              file,
              Path.of(ClusterPath.SSL_COPY_PATH.path())
              .resolve(Optional.ofNullable(file.getFileName())
                  .map(Object::toString)
                  .orElseThrow()))));
    }
  }

  private boolean testPostgresSsl(ClusterContext context) {
    var postgresCredentials = PostgresUtil.getPostgresCredentials(context, secretFinder);
    try (Connection connection = postgresConnectionManager.getConnection(
        "localhost",
        EnvoyUtil.PG_PORT,
        "postgres",
        postgresCredentials.username(),
        postgresCredentials.password(),
        Map.entry("sslmode", "require"))) {
      return true;
    } catch (SQLException ex) {
      return false;
    }
  }

}
