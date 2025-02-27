/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.cluster.controller;

import static io.stackgres.common.patroni.StackGresPasswordKeys.SUPERUSER_DATABASE;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.Secret;
import io.stackgres.cluster.common.PostgresUtil;
import io.stackgres.common.ClusterContext;
import io.stackgres.common.ClusterPath;
import io.stackgres.common.EnvoyUtil;
import io.stackgres.common.FileSystemHandler;
import io.stackgres.common.crd.sgpooling.StackGresPoolingConfig;
import io.stackgres.common.crd.sgpooling.StackGresPoolingConfigPgBouncer;
import io.stackgres.common.crd.sgpooling.StackGresPoolingConfigPgBouncerPgbouncerIni;
import io.stackgres.common.crd.sgpooling.StackGresPoolingConfigSpec;
import io.stackgres.common.postgres.PostgresConnectionManager;
import io.stackgres.common.resource.CustomResourceFinder;
import io.stackgres.common.resource.ResourceFinder;
import org.jooq.lambda.Seq;

public class PgBouncerAuthFileReconciliator {

  private static final Path ORIGINAL_AUTH_FILE_PATH =
      Paths.get(ClusterPath.PGBOUNCER_AUTH_FILE_PATH.path() + ".original");
  private static final Path AUTH_FILE_PATH =
      Paths.get(ClusterPath.PGBOUNCER_AUTH_FILE_PATH.path());
  private static final String SELECT_PGBOUNCER_USERS_FROM_PG_SHADOW =
      "SELECT '\"' || usename || '\" \"' || passwd || '\"'"
          + " FROM pg_shadow where usename = ANY (?)";

  private final String podName;
  private final ResourceFinder<Pod> podFinder;
  private final CustomResourceFinder<StackGresPoolingConfig> poolingConfigFinder;
  private final ResourceFinder<Secret> secretFinder;
  private final PostgresConnectionManager postgresConnectionManager;
  private final FileSystemHandler fileSystemHandler;

  public PgBouncerAuthFileReconciliator(
      String podName,
      ResourceFinder<Pod> podFinder,
      CustomResourceFinder<StackGresPoolingConfig> poolingConfigFinder,
      ResourceFinder<Secret> secretFinder,
      PostgresConnectionManager postgresConnectionManager,
      FileSystemHandler fileSystemHandler) {
    super();
    this.podName = podName;
    this.podFinder = podFinder;
    this.poolingConfigFinder = poolingConfigFinder;
    this.postgresConnectionManager = postgresConnectionManager;
    this.secretFinder = secretFinder;
    this.fileSystemHandler = fileSystemHandler;
  }

  public void updatePgbouncerUsersInAuthFile(ClusterContext context)
      throws IOException, SQLException {
    Optional<Pod> pod = podFinder
        .findByNameAndNamespace(podName,
            context.getCluster().getMetadata().getNamespace());
    final boolean isReady = pod
        .map(Pod::getStatus)
        .map(PodStatus::getContainerStatuses)
        .filter(Predicate.not(List::isEmpty))
        .map(containerStatuses -> containerStatuses.stream()
            .map(ContainerStatus::getReady)
            .allMatch(ready -> Optional.ofNullable(ready).orElse(false)))
        .orElse(false);
    if (!isReady) {
      return;
    }
    if (!fileSystemHandler.exists(ORIGINAL_AUTH_FILE_PATH)) {
      fileSystemHandler.copyOrReplace(AUTH_FILE_PATH, ORIGINAL_AUTH_FILE_PATH);
    }
    Collection<String> users = getPoolingConfigUserNames(context);
    var postgresCredentials = PostgresUtil.getPostgresCredentials(context, secretFinder);
    final String usersSection = extractAuthFileSectionForUsers(
        postgresCredentials.username(), postgresCredentials.password(), users);
    try (
        InputStream originalInputStream = fileSystemHandler.newInputStream(
            ORIGINAL_AUTH_FILE_PATH);
        InputStream additionalInputStream = new ByteArrayInputStream(
            usersSection.getBytes(StandardCharsets.UTF_8));
        SequenceInputStream inputStream = new SequenceInputStream(
            originalInputStream, additionalInputStream)) {
      fileSystemHandler.copyOrReplace(inputStream, AUTH_FILE_PATH);
    }
  }

  private Collection<String> getPoolingConfigUserNames(ClusterContext context) {
    StackGresPoolingConfig poolingConfig = poolingConfigFinder.findByNameAndNamespace(
        context.getCluster().getSpec().getConfigurations().getSgPoolingConfig(),
        context.getCluster().getMetadata().getNamespace())
        .orElseThrow(() -> new RuntimeException("Can not find pool config "
            + context.getCluster().getSpec().getConfigurations().getSgPoolingConfig()));
    return Optional.of(poolingConfig)
        .map(StackGresPoolingConfig::getSpec)
        .map(StackGresPoolingConfigSpec::getPgBouncer)
        .map(StackGresPoolingConfigPgBouncer::getPgbouncerIni)
        .map(StackGresPoolingConfigPgBouncerPgbouncerIni::getUsers)
        .<Collection<String>>map(Map::keySet)
        .orElseGet(List::of);
  }

  @SuppressWarnings("null")
  private String extractAuthFileSectionForUsers(
      String postgresUser,
      String postgresPassword,
      Collection<String> users)
      throws SQLException {
    List<String> authFileUsersLines = new ArrayList<>();
    try (Connection connection = postgresConnectionManager.getConnection(
        "localhost", EnvoyUtil.PG_PORT,
        SUPERUSER_DATABASE,
        postgresUser,
        postgresPassword);
        PreparedStatement statement = connection.prepareStatement(
            SELECT_PGBOUNCER_USERS_FROM_PG_SHADOW)) {
      statement.setArray(1, connection.createArrayOf(
          "varchar", users.toArray(new String[0])));
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          authFileUsersLines.add(resultSet.getString(1));
        }
      }
    }
    return "\n"
        + Seq.seq(authFileUsersLines).toString("\n")
        + "\n";
  }

}
