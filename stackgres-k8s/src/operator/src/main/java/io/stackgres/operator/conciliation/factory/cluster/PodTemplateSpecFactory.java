/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.factory.cluster;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.AffinityBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorRequirementBuilder;
import io.fabric8.kubernetes.api.model.NodeAffinity;
import io.fabric8.kubernetes.api.model.NodeSelector;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodAffinityTermBuilder;
import io.fabric8.kubernetes.api.model.PodAntiAffinityBuilder;
import io.fabric8.kubernetes.api.model.PodSecurityContext;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.TolerationBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.stackgres.common.LabelFactoryForCluster;
import io.stackgres.common.StackGresContainer;
import io.stackgres.common.StackGresContext;
import io.stackgres.common.StackGresInitContainer;
import io.stackgres.common.StackGresPort;
import io.stackgres.common.StackGresProperty;
import io.stackgres.common.StackGresVolume;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgcluster.StackGresClusterNonProduction;
import io.stackgres.common.crd.sgcluster.StackGresClusterPod;
import io.stackgres.common.crd.sgcluster.StackGresClusterPodScheduling;
import io.stackgres.common.crd.sgcluster.StackGresClusterSpec;
import io.stackgres.common.crd.sgcluster.StackGresClusterSpecLabels;
import io.stackgres.common.crd.sgcluster.StackGresClusterSpecMetadata;
import io.stackgres.operator.conciliation.ContainerFactoryDiscoverer;
import io.stackgres.operator.conciliation.InitContainerFactoryDiscover;
import io.stackgres.operator.conciliation.OperatorVersionBinder;
import io.stackgres.operator.conciliation.cluster.StackGresClusterContext;
import io.stackgres.operator.conciliation.factory.ContainerFactory;
import io.stackgres.operator.conciliation.factory.ImmutablePodTemplateResult;
import io.stackgres.operator.conciliation.factory.PodTemplateFactory;
import io.stackgres.operator.conciliation.factory.PodTemplateResult;
import io.stackgres.operator.conciliation.factory.ResourceFactory;
import io.stackgres.operator.conciliation.factory.cluster.patroni.PatroniRoleGenerator;
import org.jooq.lambda.Seq;

@Singleton
@OperatorVersionBinder
public class PodTemplateSpecFactory
    implements PodTemplateFactory<ClusterContainerContext> {

  private final ResourceFactory<StackGresClusterContext, PodSecurityContext> podSecurityContext;

  private final LabelFactoryForCluster<StackGresCluster> labelFactory;

  private final ContainerFactoryDiscoverer<ClusterContainerContext>
      containerFactoryDiscoverer;

  private final InitContainerFactoryDiscover<ClusterContainerContext>
      initContainerFactoryDiscoverer;

  @Inject
  public PodTemplateSpecFactory(
      ResourceFactory<StackGresClusterContext, PodSecurityContext> podSecurityContext,
      LabelFactoryForCluster<StackGresCluster> labelFactory,
      ContainerFactoryDiscoverer<ClusterContainerContext> containerFactoryDiscoverer,
      InitContainerFactoryDiscover<ClusterContainerContext>
          initContainerFactoryDiscoverer) {
    this.podSecurityContext = podSecurityContext;
    this.labelFactory = labelFactory;
    this.containerFactoryDiscoverer = containerFactoryDiscoverer;
    this.initContainerFactoryDiscoverer = initContainerFactoryDiscoverer;
  }

  @Override
  public PodTemplateResult getPodTemplateSpec(ClusterContainerContext context) {

    final List<ContainerFactory<ClusterContainerContext>> containerFactories =
        containerFactoryDiscoverer.discoverContainers(context);

    final List<Container> containers = containerFactories.stream()
        .map(f -> f.getContainer(context)).toList();

    Map<String, String> componentVersions = containerFactories.stream()
        .map(f -> f.getComponentVersions(context))
        .map(Map::entrySet)
        .flatMap(Set::stream)
        .distinct()
        .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

    final List<ContainerFactory<ClusterContainerContext>> initContainerFactories =
        initContainerFactoryDiscoverer.discoverContainers(context);

    List<Container> initContainers = initContainerFactories
        .stream().map(f -> f.getContainer(context))
        .toList();

    final List<String> claimedVolumes = Stream.concat(containers.stream(), initContainers.stream())
        .flatMap(container -> container.getVolumeMounts().stream())
        .map(VolumeMount::getName)
        .distinct()
        .toList();

    claimedVolumes.forEach(rv -> {
      if (!context.availableVolumes().containsKey(rv) && !context.getDataVolumeName().equals(rv)) {
        throw new IllegalStateException("Volume " + rv + " is required but not available");
      }
    });

    List<Volume> volumes = claimedVolumes.stream()
        .map(volumeName -> context.availableVolumes().get(volumeName))
        .filter(Objects::nonNull)
        .toList();

    StackGresCluster cluster = context.getClusterContext().getSource();
    final Map<String, String> podLabels = labelFactory.statefulSetPodLabels(cluster);
    final Map<String, String> customPodLabels = podsCustomLabels(cluster);

    var podTemplate = new PodTemplateSpecBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .addToLabels(customPodLabels)
            .addToLabels(podLabels)
            .addToAnnotations(StackGresContext.VERSION_KEY,
                StackGresProperty.OPERATOR_VERSION.getString())
            .addToAnnotations(componentVersions)
            .build())
        .withNewSpec()
        .withAffinity(Optional.of(new AffinityBuilder())
            .filter(ignore -> isClusterPodAntiAffinityEnabled(cluster)
                || hasNonEmptyPodNodeAffinity(cluster))
            .map(builder -> builder.withNodeAffinity(
                Optional.of(cluster.getSpec())
                .map(StackGresClusterSpec::getPod)
                .map(StackGresClusterPod::getScheduling)
                .map(StackGresClusterPodScheduling::getNodeAffinity)
                .orElse(null)))
            .map(builder -> builder.withPodAntiAffinity(Optional.of(new PodAntiAffinityBuilder()
                .addToRequiredDuringSchedulingIgnoredDuringExecution(
                    new PodAffinityTermBuilder()
                    .withLabelSelector(new LabelSelectorBuilder()
                        .withMatchExpressions(new LabelSelectorRequirementBuilder()
                            .withKey(StackGresContext.APP_KEY)
                            .withOperator("In")
                            .withValues(labelFactory.labelMapper().appName())
                            .build(),
                            new LabelSelectorRequirementBuilder()
                            .withKey(labelFactory.labelMapper().clusterKey(cluster))
                            .withOperator("In")
                            .withValues("true")
                            .build())
                        .build())
                    .withTopologyKey("kubernetes.io/hostname")
                    .build())
                .build())
                .filter(ignore -> isClusterPodAntiAffinityEnabled(cluster))
                .orElse(null)))
            .map(builder -> builder.build())
            .orElse(null))
        .withNodeSelector(Optional.ofNullable(cluster.getSpec())
            .map(StackGresClusterSpec::getPod)
            .map(StackGresClusterPod::getScheduling)
            .map(StackGresClusterPodScheduling::getNodeSelector)
            .orElse(null))
        .withTolerations(Optional.ofNullable(cluster.getSpec())
            .map(StackGresClusterSpec::getPod)
            .map(StackGresClusterPod::getScheduling)
            .map(StackGresClusterPodScheduling::getTolerations)
            .map(tolerations -> Seq.seq(tolerations)
                .map(TolerationBuilder::new)
                .map(TolerationBuilder::build)
                .toList())
            .orElse(null))
        .withShareProcessNamespace(Boolean.TRUE)
        .withServiceAccountName(PatroniRoleGenerator.roleName(context.getClusterContext()))
        .withSecurityContext(podSecurityContext.createResource(context.getClusterContext()))
        .withVolumes(volumes)
        .addAllToVolumes(Optional.of(cluster)
            .map(StackGresCluster::getSpec)
            .map(StackGresClusterSpec::getPod)
            .map(StackGresClusterPod::getCustomVolumes)
            .stream()
            .flatMap(List::stream)
            .map(VolumeBuilder::new)
            .map(builder -> builder.withName(StackGresVolume.CUSTOM.getName(builder.getName())))
            .map(VolumeBuilder::build)
            .toList())
        .withContainers(containers)
        .addAllToContainers(Optional.of(cluster)
            .map(StackGresCluster::getSpec)
            .map(StackGresClusterSpec::getPod)
            .map(StackGresClusterPod::getCustomContainers)
            .stream()
            .flatMap(List::stream)
            .map(ContainerBuilder::new)
            .map(builder -> builder.withName(
                StackGresContainer.CUSTOM.formatted(builder.getName())))
            .map(builder -> builder.withPorts(
                Optional.ofNullable(builder.buildPorts())
                .stream()
                .flatMap(List::stream)
                .map(ContainerPortBuilder::new)
                .map(containerPortBuilder -> containerPortBuilder.withName(
                    StackGresPort.CUSTOM.getName(containerPortBuilder.getName())))
                .map(ContainerPortBuilder::build)
                .toList()))
            .map(ContainerBuilder::build)
            .toList())
        .withInitContainers(initContainers)
        .addAllToInitContainers(Optional.of(cluster)
            .map(StackGresCluster::getSpec)
            .map(StackGresClusterSpec::getPod)
            .map(StackGresClusterPod::getCustomInitContainers)
            .stream()
            .flatMap(List::stream)
            .map(ContainerBuilder::new)
            .map(builder -> builder.withName(
                StackGresInitContainer.CUSTOM.formatted(builder.getName())))
            .map(builder -> builder.withPorts(
                Optional.ofNullable(builder.buildPorts())
                .stream()
                .flatMap(List::stream)
                .map(ContainerPortBuilder::new)
                .map(containerPortBuilder -> containerPortBuilder.withName(
                    StackGresPort.CUSTOM.getName(containerPortBuilder.getName())))
                .map(ContainerPortBuilder::build)
                .toList()))
            .map(ContainerBuilder::build)
            .toList())
        .withTerminationGracePeriodSeconds(60L)
        .endSpec()
        .build();

    return ImmutablePodTemplateResult.builder()
        .spec(podTemplate)
        .claimedVolumes(claimedVolumes)
        .build();
  }

  private Boolean isClusterPodAntiAffinityEnabled(StackGresCluster cluster) {
    return Optional.ofNullable(
        cluster.getSpec().getNonProductionOptions())
        .map(StackGresClusterNonProduction::getDisableClusterPodAntiAffinity)
        .map(disableClusterPodAntiAffinity -> !disableClusterPodAntiAffinity)
        .orElse(true);
  }

  private boolean hasNonEmptyPodNodeAffinity(StackGresCluster cluster) {
    return Optional.of(cluster.getSpec())
        .map(StackGresClusterSpec::getPod)
        .map(StackGresClusterPod::getScheduling)
        .map(StackGresClusterPodScheduling::getNodeAffinity)
        .map(this::isNonEmptyNodeAffinity)
        .orElse(false);
  }

  private boolean isNonEmptyNodeAffinity(NodeAffinity nodeAffinity) {
    return Optional.ofNullable(nodeAffinity.getPreferredDuringSchedulingIgnoredDuringExecution())
        .map(list -> !list.isEmpty())
        .orElse(false)
        || Optional.ofNullable(nodeAffinity.getRequiredDuringSchedulingIgnoredDuringExecution())
        .map(NodeSelector::getNodeSelectorTerms)
        .map(list -> !list.isEmpty())
        .orElse(false);
  }

  public Map<String, String> podsCustomLabels(StackGresCluster cluster) {
    return Optional.ofNullable(cluster)
        .map(StackGresCluster::getSpec)
        .map(StackGresClusterSpec::getMetadata)
        .map(StackGresClusterSpecMetadata::getLabels)
        .map(StackGresClusterSpecLabels::getClusterPods)
        .orElse(Map.of());
  }
}
