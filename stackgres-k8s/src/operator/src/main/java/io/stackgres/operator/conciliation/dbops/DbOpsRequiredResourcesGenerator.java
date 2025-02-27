/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.dbops;

import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgcluster.StackGresClusterSpec;
import io.stackgres.common.crd.sgconfig.StackGresConfig;
import io.stackgres.common.crd.sgdbops.StackGresDbOps;
import io.stackgres.common.crd.sgdbops.StackGresDbOpsBenchmark;
import io.stackgres.common.crd.sgdbops.StackGresDbOpsBenchmarkStatus;
import io.stackgres.common.crd.sgdbops.StackGresDbOpsPgbench;
import io.stackgres.common.crd.sgdbops.StackGresDbOpsSamplingStatus;
import io.stackgres.common.crd.sgdbops.StackGresDbOpsSpec;
import io.stackgres.common.crd.sgdbops.StackGresDbOpsStatus;
import io.stackgres.common.crd.sgprofile.StackGresProfile;
import io.stackgres.common.resource.CustomResourceFinder;
import io.stackgres.common.resource.CustomResourceScanner;
import io.stackgres.operator.conciliation.RequiredResourceGenerator;
import io.stackgres.operator.conciliation.ResourceGenerationDiscoverer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class DbOpsRequiredResourcesGenerator
    implements RequiredResourceGenerator<StackGresDbOps> {

  protected static final Logger LOGGER = LoggerFactory
      .getLogger(DbOpsRequiredResourcesGenerator.class);

  private final CustomResourceScanner<StackGresConfig> configScanner;

  private final CustomResourceFinder<StackGresCluster> clusterFinder;

  private final CustomResourceFinder<StackGresProfile> profileFinder;

  private final CustomResourceFinder<StackGresDbOps> dbOpsFinder;

  private final ResourceGenerationDiscoverer<StackGresDbOpsContext> discoverer;

  @Inject
  public DbOpsRequiredResourcesGenerator(
      CustomResourceScanner<StackGresConfig> configScanner,
      CustomResourceFinder<StackGresCluster> clusterFinder,
      CustomResourceFinder<StackGresProfile> profileFinder,
      CustomResourceFinder<StackGresDbOps> dbOpsFinder,
      ResourceGenerationDiscoverer<StackGresDbOpsContext> discoverer) {
    this.configScanner = configScanner;
    this.clusterFinder = clusterFinder;
    this.profileFinder = profileFinder;
    this.dbOpsFinder = dbOpsFinder;
    this.discoverer = discoverer;
  }

  @Override
  public List<HasMetadata> getRequiredResources(StackGresDbOps dbOps) {
    final ObjectMeta metadata = dbOps.getMetadata();
    final String dbOpsNamespace = metadata.getNamespace();

    final StackGresConfig config = configScanner.findResources()
        .stream()
        .filter(list -> list.size() == 1)
        .flatMap(List::stream)
        .findAny()
        .orElseThrow(() -> new IllegalArgumentException(
            "SGConfig not found or more than one exists. Aborting reoconciliation!"));

    final StackGresDbOpsSpec spec = dbOps.getSpec();
    final Optional<StackGresCluster> cluster = clusterFinder
        .findByNameAndNamespace(spec.getSgCluster(), dbOpsNamespace);

    final Optional<StackGresProfile> profile = cluster
        .map(StackGresCluster::getSpec)
        .map(StackGresClusterSpec::getSgInstanceProfile)
        .flatMap(profileName -> profileFinder
            .findByNameAndNamespace(profileName, dbOpsNamespace));

    final Optional<StackGresDbOpsSamplingStatus> samplingStatus = Optional.of(dbOps.getSpec())
        .map(StackGresDbOpsSpec::getBenchmark)
        .map(StackGresDbOpsBenchmark::getPgbench)
        .map(StackGresDbOpsPgbench::getSamplingSgDbOps)
        .map(samplingDbOpsName -> dbOpsFinder
            .findByNameAndNamespace(samplingDbOpsName, dbOpsNamespace)
            .map(StackGresDbOps::getStatus)
            .map(StackGresDbOpsStatus::getBenchmark)
            .map(StackGresDbOpsBenchmarkStatus::getSampling)
            .orElseThrow(() -> new IllegalArgumentException(
                "SGDbOps " + samplingDbOpsName
                + " was not found or has no has no sampling status")));

    StackGresDbOpsContext context = ImmutableStackGresDbOpsContext.builder()
        .config(config)
        .source(dbOps)
        .foundCluster(cluster)
        .foundProfile(profile)
        .samplingStatus(samplingStatus)
        .build();

    return discoverer.generateResources(context);
  }

}
