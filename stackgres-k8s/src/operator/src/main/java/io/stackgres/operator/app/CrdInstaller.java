/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.app;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionVersion;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.impl.BaseClient;
import io.stackgres.common.CrdLoader;
import io.stackgres.common.OperatorProperty;
import io.stackgres.common.StackGresContext;
import io.stackgres.common.StackGresProperty;
import io.stackgres.common.StackGresVersion;
import io.stackgres.common.YamlMapperProvider;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgdistributedlogs.StackGresDistributedLogs;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.kubernetesclient.ProxiedKubernetesClientProducer.KubernetesClientInvocationHandler;
import io.stackgres.common.resource.ResourceFinder;
import io.stackgres.common.resource.ResourceWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jetbrains.annotations.NotNull;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class CrdInstaller {

  private static final long OLDEST = StackGresVersion.OLDEST.getVersionAsNumber();

  private static final Logger LOGGER = LoggerFactory.getLogger(CrdInstaller.class);

  private final List<String> allowedNamespaces = OperatorProperty.getAllowedNamespaces();
  private final boolean clusterRoleDisabled = OperatorProperty.CLUSTER_ROLE_DISABLED.getBoolean();

  private final ResourceFinder<CustomResourceDefinition> crdResourceFinder;
  private final ResourceWriter<CustomResourceDefinition> crdResourceWriter;
  private final CrdLoader crdLoader;
  private final KubernetesClient client;

  @Inject
  public CrdInstaller(
      ResourceFinder<CustomResourceDefinition> crdResourceFinder,
      ResourceWriter<CustomResourceDefinition> crdResourceWriter,
      YamlMapperProvider yamlMapperProvider,
      KubernetesClient client) {
    this.crdResourceFinder = crdResourceFinder;
    this.crdResourceWriter = crdResourceWriter;
    this.client = client;
    this.crdLoader = new CrdLoader(yamlMapperProvider.get());
  }

  public void checkUpgrade() {
    var resourcesRequiringUpgrade = crdLoader.scanCrds().stream()
        .map(crd -> Optional.of(clusterRoleDisabled)
            .filter(clusterRoleDisabled -> clusterRoleDisabled)
            .map(clusterRoleDisabled -> crd)
            .or(() -> crdResourceFinder.findByName(crd.getMetadata().getName())))
        .flatMap(Optional::stream)
        .flatMap(crd -> listCrdResources(crd)
          .stream()
          .map(resource -> Tuple.tuple(resource, Optional.of(resource)
              .map(StackGresVersion::getStackGresVersionFromResourceAsNumber)
              .filter(version -> version < OLDEST)))
          .filter(t -> t.v2.isPresent())
          .map(t -> t.map2(Optional::get))
          .map(t -> t.concat("version at " + StackGresVersion
              .getStackGresRawVersionFromResource(t.v1)))
          .filter(t -> List.of(
              HasMetadata.getKind(StackGresCluster.class),
              HasMetadata.getKind(StackGresShardedCluster.class),
              HasMetadata.getKind(StackGresDistributedLogs.class))
              .contains(t.v1.getKind())))
        .toList();
    if (!resourcesRequiringUpgrade.isEmpty()) {
      throw new RuntimeException("Can not upgrade due to some resources still at version"
          + " older than \"" + StackGresVersion.OLDEST.getVersion() + "\"."
          + " Please, downgrade to a previous version of the operator and run a SGDbOps of"
          + " type securityUpgrade on all the SGClusters (that are not part of an SGShardedCluster),"
          + " a SGShardedDbOps of type securityUpgrade on all the SGShardedClusters and perform the"
          + " upgrade procedure as explained in https://stackgres.io/doc/latest/administration/distributed-logs/upgrade/"
          + " of the following list:\n"
          + resourcesRequiringUpgrade.stream()
          .map(t -> t.v1.getKind() + " "
              + t.v1.getMetadata().getNamespace() + "."
              + t.v1.getMetadata().getName() + ": " + t.v3)
          .collect(Collectors.joining("\n")));
    }
  }

  List<GenericKubernetesResource> listCrdResources(CustomResourceDefinition crd) {
    var genericKubernetesResources =
        client.genericKubernetesResources(CustomResourceDefinitionContext.fromCrd(crd));
    return Optional.of(allowedNamespaces)
        .filter(Predicate.not(List::isEmpty))
        .map(allowedNamespaces -> allowedNamespaces.stream()
            .flatMap(allowedNamespace -> Optional
                .ofNullable(genericKubernetesResources
                    .inNamespace(allowedNamespace)
                    .list()
                    .getItems()).stream())
            .reduce(Seq.<GenericKubernetesResource>of(), (seq, items) -> seq.append(items), (u, v) -> v)
            .toList())
        .orElseGet(() -> genericKubernetesResources
            .inAnyNamespace()
            .list()
            .getItems());
  }

  public void installCustomResourceDefinitions() {
    LOGGER.info("Installing CRDs");
    crdLoader.scanCrds()
        .stream()
        .forEach(this::installCrd);
  }

  protected void installCrd(@NotNull CustomResourceDefinition currentCrd) {
    String name = currentCrd.getMetadata().getName();
    LOGGER.info("Installing CRD {}", name);
    Optional<CustomResourceDefinition> installedCrdOpt = crdResourceFinder
        .findByName(name);

    if (installedCrdOpt.isPresent()) {
      LOGGER.debug("CRD {} is present, patching it", name);
      CustomResourceDefinition installedCrd = installedCrdOpt.get();
      if (!isCrdInstalled(currentCrd, installedCrd)) {
        upgradeCrd(currentCrd, installedCrd);
      }
      updateAlreadyInstalledVersions(currentCrd, installedCrd);
      crdResourceWriter.update(installedCrd, foundCrd -> {
        setOperatorVersion(foundCrd);
        foundCrd.setSpec(installedCrd.getSpec());
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("Updating CRD:\n{}",
              serializeToJsonAsKubernetesClient(foundCrd));
        }
      });
    } else {
      LOGGER.info("CRD {} is not present, installing it", name);
      setOperatorVersion(currentCrd);
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("Creating CRD:\n{}", serializeToJsonAsKubernetesClient(currentCrd));
      }
      crdResourceWriter.create(currentCrd);
    }
  }

  private String serializeToJsonAsKubernetesClient(CustomResourceDefinition foundCrd) {
    try {
      return BaseClient.class.cast(
          KubernetesClientInvocationHandler.class.cast(
              Proxy.getInvocationHandler(client)).getClient())
          .getKubernetesSerialization().asJson(foundCrd);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private void updateAlreadyInstalledVersions(
      CustomResourceDefinition currentCrd,
      CustomResourceDefinition installedCrd) {
    installedCrd.getSpec().getVersions().forEach(installedVersion -> {
      currentCrd.getSpec()
          .getVersions()
          .stream()
          .filter(v -> v.getName().equals(installedVersion.getName()))
          .forEach(currentVersion -> updateAlreadyDeployedVersion(
              installedVersion, currentVersion));
    });
  }

  private void updateAlreadyDeployedVersion(CustomResourceDefinitionVersion installedVersion,
      CustomResourceDefinitionVersion currentVersion) {
    installedVersion.setSchema(currentVersion.getSchema());
    installedVersion.setSubresources(currentVersion.getSubresources());
    installedVersion.setAdditionalPrinterColumns(currentVersion.getAdditionalPrinterColumns());
  }

  private void upgradeCrd(
      CustomResourceDefinition currentCrd,
      CustomResourceDefinition installedCrd) {
    disableStorageVersions(installedCrd);
    addNewSchemaVersions(currentCrd, installedCrd);
    crdResourceWriter.update(installedCrd, foundCrd -> {
      foundCrd.setSpec(installedCrd.getSpec());
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("Updating CRD:\n{}",
            serializeToJsonAsKubernetesClient(foundCrd));
      }
    });
  }

  private void setOperatorVersion(CustomResourceDefinition crd) {
    if (crd.getMetadata().getAnnotations() == null) {
      crd.getMetadata().setAnnotations(new HashMap<>());
    }
    crd.getMetadata().getAnnotations().put(
        StackGresContext.VERSION_KEY, StackGresProperty.OPERATOR_VERSION.getString());
  }

  private void disableStorageVersions(CustomResourceDefinition installedCrd) {
    installedCrd.getSpec().getVersions()
        .forEach(versionDefinition -> versionDefinition.setStorage(false));
  }

  private void addNewSchemaVersions(
      CustomResourceDefinition currentCrd,
      CustomResourceDefinition installedCrd) {
    List<String> installedVersions = installedCrd.getSpec().getVersions()
        .stream()
        .map(CustomResourceDefinitionVersion::getName)
        .toList();

    List<String> versionsToInstall = currentCrd.getSpec().getVersions()
        .stream()
        .map(CustomResourceDefinitionVersion::getName)
        .filter(Predicate.not(installedVersions::contains))
        .toList();

    currentCrd.getSpec().getVersions().stream()
        .filter(version -> versionsToInstall.contains(version.getName()))
        .forEach(installedCrd.getSpec().getVersions()::add);
  }

  private boolean isCrdInstalled(
      CustomResourceDefinition crd,
      CustomResourceDefinition installedCrd) {
    final String currentVersion = crd.getSpec().getVersions()
        .stream()
        .filter(CustomResourceDefinitionVersion::getStorage).findFirst()
        .orElseThrow(() -> new RuntimeException("At least one CRD version must be stored"))
        .getName();
    return installedCrd.getSpec().getVersions().stream()
        .map(CustomResourceDefinitionVersion::getName)
        .anyMatch(installedVersion -> installedVersion.equals(currentVersion));
  }

  public void checkCustomResourceDefinitions() {
    crdLoader.scanCrds()
        .forEach(this::checkCrd);
  }

  protected void checkCrd(@NotNull CustomResourceDefinition currentCrd) {
    Optional<CustomResourceDefinition> installedCrdOpt = Optional.of(clusterRoleDisabled)
        .filter(clusterRoleDisabled -> clusterRoleDisabled)
        .map(clusterRoleDisabled -> currentCrd)
        .or(() -> crdResourceFinder.findByName(currentCrd.getMetadata().getName()));

    if (installedCrdOpt.isEmpty()) {
      throw new RuntimeException("CRD " + currentCrd.getMetadata().getName() + " was not found.");
    }
  }

}
