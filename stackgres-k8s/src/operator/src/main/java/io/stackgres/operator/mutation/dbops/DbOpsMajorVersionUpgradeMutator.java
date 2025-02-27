/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.mutation.dbops;

import static io.stackgres.common.StackGresUtil.getPostgresFlavorComponent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Predicates;
import io.stackgres.common.BackupStorageUtil;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgcluster.StackGresClusterConfigurations;
import io.stackgres.common.crd.sgcluster.StackGresClusterSpec;
import io.stackgres.common.crd.sgdbops.StackGresDbOps;
import io.stackgres.common.resource.CustomResourceFinder;
import io.stackgres.operator.common.StackGresDbOpsReview;
import io.stackgres.operatorframework.admissionwebhook.Operation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DbOpsMajorVersionUpgradeMutator implements DbOpsMutator {

  private final CustomResourceFinder<StackGresCluster> clusterFinder;
  private final Instant defaultTimestamp;

  @Inject
  public DbOpsMajorVersionUpgradeMutator(
      CustomResourceFinder<StackGresCluster> clusterFinder) {
    this.clusterFinder = clusterFinder;
    this.defaultTimestamp = null;
  }

  DbOpsMajorVersionUpgradeMutator(
      CustomResourceFinder<StackGresCluster> clusterFinder,
      Instant defaultTimestamp) {
    this.clusterFinder = clusterFinder;
    this.defaultTimestamp = defaultTimestamp;
  }

  @Override
  public StackGresDbOps mutate(StackGresDbOpsReview review, StackGresDbOps resource) {
    if (review.getRequest().getOperation() != Operation.CREATE
        && review.getRequest().getOperation() != Operation.UPDATE) {
      return resource;
    }

    if (resource.getSpec() != null
        && resource.getSpec().getSgCluster() != null
        && resource.getSpec().getMajorVersionUpgrade() != null
        && resource.getSpec().getMajorVersionUpgrade().getPostgresVersion() != null
        && resource.getSpec().getMajorVersionUpgrade().getBackupPath() == null) {
      Optional<StackGresCluster> cluster = clusterFinder.findByNameAndNamespace(
          resource.getSpec().getSgCluster(),
          resource.getMetadata().getNamespace());
      if (cluster.filter(this::clusterHasBackups).isPresent()) {
        final String backupPath = getBackupPath(resource, cluster.get());
        resource.getSpec().getMajorVersionUpgrade().setBackupPath(backupPath);
      }
    }

    return resource;
  }

  private boolean clusterHasBackups(StackGresCluster cluster) {
    var configurations = Optional.of(cluster)
        .map(StackGresCluster::getSpec)
        .map(StackGresClusterSpec::getConfigurations);
    return configurations.map(StackGresClusterConfigurations::getBackups)
        .filter(Predicates.not(List::isEmpty))
        .isPresent();
  }

  private String getBackupPath(StackGresDbOps dbOps, StackGresCluster cluster) {
    final String postgresVersion = dbOps.getSpec()
        .getMajorVersionUpgrade().getPostgresVersion();
    final String postgresFlavor = cluster.getSpec()
        .getPostgres().getFlavor();
    final String postgresMajorVersion = getPostgresFlavorComponent(postgresFlavor)
        .get(cluster)
        .getMajorVersion(postgresVersion);
    Instant timestamp = Optional.ofNullable(defaultTimestamp).orElse(Instant.now());
    return BackupStorageUtil.getPath(
        cluster.getMetadata().getNamespace(),
        cluster.getMetadata().getName(),
        timestamp,
        postgresMajorVersion);
  }

}
