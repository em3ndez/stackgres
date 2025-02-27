/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.factory.dbops;

import static io.stackgres.common.DbOpsUtil.jobName;
import static io.stackgres.common.StackGresUtil.getDefaultPullPolicy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.AffinityBuilder;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.PodSecurityContext;
import io.fabric8.kubernetes.api.model.TolerationBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.stackgres.common.OperatorProperty;
import io.stackgres.common.StackGresContext;
import io.stackgres.common.StackGresProperty;
import io.stackgres.common.StackGresUtil;
import io.stackgres.common.crd.sgconfig.StackGresConfigDeveloper;
import io.stackgres.common.crd.sgconfig.StackGresConfigDeveloperContainerPatches;
import io.stackgres.common.crd.sgconfig.StackGresConfigDeveloperPatches;
import io.stackgres.common.crd.sgconfig.StackGresConfigSpec;
import io.stackgres.common.crd.sgdbops.StackGresDbOps;
import io.stackgres.common.crd.sgdbops.StackGresDbOpsSpec;
import io.stackgres.common.crd.sgdbops.StackGresDbOpsSpecScheduling;
import io.stackgres.common.labels.LabelFactoryForDbOps;
import io.stackgres.operator.conciliation.OperatorVersionBinder;
import io.stackgres.operator.conciliation.dbops.StackGresDbOpsContext;
import io.stackgres.operator.conciliation.factory.ResourceFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.lambda.Seq;

@Singleton
@OperatorVersionBinder
@DbOpsJob("restart")
public class DbOpsRestartJob implements DbOpsJobFactory {

  private final LabelFactoryForDbOps dbOpsLabelFactory;
  private final ResourceFactory<StackGresDbOpsContext, PodSecurityContext> podSecurityFactory;

  @Inject
  public DbOpsRestartJob(
      LabelFactoryForDbOps dbOpsLabelFactory,
      ResourceFactory<StackGresDbOpsContext, PodSecurityContext> podSecurityFactory) {
    this.dbOpsLabelFactory = dbOpsLabelFactory;
    this.podSecurityFactory = podSecurityFactory;
  }

  @Override
  public Job createJob(StackGresDbOpsContext context) {
    StackGresDbOps dbOps = context.getSource();
    String namespace = dbOps.getMetadata().getNamespace();
    final Map<String, String> labels = dbOpsLabelFactory.dbOpsPodLabels(context.getSource());
    final Integer maxRetries = Optional.of(dbOps)
        .map(StackGresDbOps::getSpec)
        .map(StackGresDbOpsSpec::getMaxRetries)
        .orElse(0);
    return new JobBuilder()
        .withNewMetadata()
        .withNamespace(namespace)
        .withName(jobName(dbOps, "restart"))
        .withLabels(labels)
        .endMetadata()
        .withNewSpec()
        .withBackoffLimit(maxRetries)
        .withParallelism(1)
        .withNewTemplate()
        .withNewMetadata()
        .withNamespace(namespace)
        .withName(jobName(dbOps))
        .withLabels(labels)
        .endMetadata()
        .withNewSpec()
        .withSecurityContext(podSecurityFactory.createResource(context))
        .withRestartPolicy("Never")
        .withServiceAccountName(DbOpsRole.roleName(context))
        .withNodeSelector(Optional.ofNullable(dbOps)
            .map(StackGresDbOps::getSpec)
            .map(StackGresDbOpsSpec::getScheduling)
            .map(StackGresDbOpsSpecScheduling::getNodeSelector)
            .orElse(null))
        .withTolerations(Optional.ofNullable(dbOps)
            .map(StackGresDbOps::getSpec)
            .map(StackGresDbOpsSpec::getScheduling)
            .map(StackGresDbOpsSpecScheduling::getTolerations)
            .map(tolerations -> Seq.seq(tolerations)
                .map(TolerationBuilder::new)
                .map(TolerationBuilder::build)
                .toList())
            .orElse(null))
        .withAffinity(new AffinityBuilder()
            .withNodeAffinity(Optional.of(dbOps)
                .map(StackGresDbOps::getSpec)
                .map(StackGresDbOpsSpec::getScheduling)
                .map(StackGresDbOpsSpecScheduling::getNodeAffinity)
                .orElse(null))
            .withPodAffinity(Optional.of(dbOps)
                .map(StackGresDbOps::getSpec)
                .map(StackGresDbOpsSpec::getScheduling)
                .map(StackGresDbOpsSpecScheduling::getPodAffinity)
                .orElse(null))
            .withPodAntiAffinity(Optional.of(dbOps)
                .map(StackGresDbOps::getSpec)
                .map(StackGresDbOpsSpec::getScheduling)
                .map(StackGresDbOpsSpecScheduling::getPodAntiAffinity)
                .orElse(null))
            .build())
        .withContainers(new ContainerBuilder()
            .withName("run-dbops")
            .withImage(StackGresUtil.getJobsImageNameWithTag(context))
            .withImagePullPolicy(getDefaultPullPolicy())
            .addToEnv(
                new EnvVarBuilder()
                    .withName(OperatorProperty.OPERATOR_NAME.getEnvironmentVariableName())
                    .withValue(OperatorProperty.OPERATOR_NAME.getString())
                    .build(),
                new EnvVarBuilder()
                    .withName(OperatorProperty.OPERATOR_NAMESPACE.getEnvironmentVariableName())
                    .withValue(OperatorProperty.OPERATOR_NAMESPACE.getString())
                    .build(),
                new EnvVarBuilder()
                    .withName("JOB_NAMESPACE")
                    .withValue(namespace)
                    .build(),
                new EnvVarBuilder()
                    .withName(StackGresProperty.OPERATOR_VERSION.getEnvironmentVariableName())
                    .withValue(StackGresProperty.OPERATOR_VERSION.getString())
                    .build(),
                new EnvVarBuilder()
                    .withName("CRD_UPGRADE")
                    .withValue(Boolean.FALSE.toString())
                    .build(),
                new EnvVarBuilder()
                    .withName("CONVERSION_WEBHOOKS")
                    .withValue(Boolean.FALSE.toString())
                    .build(),
                new EnvVarBuilder()
                    .withName("DATABASE_OPERATION_CR_NAME")
                    .withValue(dbOps.getMetadata().getName())
                    .build(),
                new EnvVarBuilder()
                    .withName("SERVICE_ACCOUNT")
                    .withNewValueFrom()
                    .withNewFieldRef()
                    .withFieldPath("spec.serviceAccountName")
                    .endFieldRef()
                    .endValueFrom()
                    .build(),
                new EnvVarBuilder()
                    .withName("POD_NAME")
                    .withNewValueFrom()
                    .withNewFieldRef()
                    .withFieldPath("metadata.name")
                    .endFieldRef()
                    .endValueFrom()
                    .build(),
                new EnvVarBuilder()
                    .withName("APP_OPTS")
                    .withValue(System.getenv("APP_OPTS"))
                    .build(),
                new EnvVarBuilder()
                    .withName("JAVA_OPTS")
                    .withValue(System.getenv("JAVA_OPTS"))
                    .build(),
                new EnvVarBuilder()
                    .withName("DEBUG_JOBS")
                    .withValue(System.getenv("DEBUG_OPERATOR"))
                    .build(),
                new EnvVarBuilder()
                    .withName("DEBUG_JOBS_SUSPEND")
                    .withValue(System.getenv("DEBUG_OPERATOR_SUSPEND"))
                    .build(),
                new EnvVarBuilder()
                    .withName("DBOPS_LOCK_DURATION")
                    .withValue(OperatorProperty.LOCK_DURATION.getString())
                    .build(),
                new EnvVarBuilder()
                    .withName("DBOPS_LOCK_POLL_INTERVAL")
                    .withValue(OperatorProperty.LOCK_POLL_INTERVAL.getString())
                    .build(),
                new EnvVarBuilder()
                    .withName("LOCK_SERVICE_ACCOUNT_KEY")
                    .withValue(StackGresContext.LOCK_SERVICE_ACCOUNT_KEY)
                    .build(),
                new EnvVarBuilder()
                    .withName("LOCK_POD_KEY")
                    .withValue(StackGresContext.LOCK_POD_KEY)
                    .build(),
                new EnvVarBuilder()
                    .withName("LOCK_TIMEOUT_KEY")
                    .withValue(StackGresContext.LOCK_TIMEOUT_KEY)
                    .build())
            .addAllToVolumeMounts(Optional.of(context.getConfig().getSpec())
                .map(StackGresConfigSpec::getDeveloper)
                .map(StackGresConfigDeveloper::getPatches)
                .map(StackGresConfigDeveloperPatches::getJobs)
                .map(StackGresConfigDeveloperContainerPatches::getVolumeMounts)
                .stream()
                .flatMap(List::stream)
                .map(VolumeMount.class::cast)
                .toList())
            .build())
        .addAllToVolumes(Seq.seq(
            Optional.of(context.getConfig().getSpec())
            .map(StackGresConfigSpec::getDeveloper)
            .map(StackGresConfigDeveloper::getPatches)
            .map(StackGresConfigDeveloperPatches::getJobs)
            .map(StackGresConfigDeveloperContainerPatches::getVolumes)
            .stream()
            .flatMap(List::stream)
            .map(Volume.class::cast))
            .grouped(volume -> volume.getName())
            .flatMap(t -> t.v2.limit(1))
            .toList())
        .endSpec()
        .endTemplate()
        .endSpec()
        .build();
  }

}
