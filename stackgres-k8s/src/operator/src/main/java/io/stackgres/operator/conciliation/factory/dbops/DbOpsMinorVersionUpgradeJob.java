/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.factory.dbops;

import static io.stackgres.operator.conciliation.factory.dbops.DbOpsUtil.jobName;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.PodSecurityContext;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.stackgres.common.LabelFactoryForDbOps;
import io.stackgres.common.OperatorProperty;
import io.stackgres.common.StackGresProperty;
import io.stackgres.common.crd.sgdbops.StackGresDbOps;
import io.stackgres.operator.common.StackGresVersion;
import io.stackgres.operator.conciliation.OperatorVersionBinder;
import io.stackgres.operator.conciliation.dbops.StackGresDbOpsContext;
import io.stackgres.operator.conciliation.factory.ResourceFactory;

@Singleton
@OperatorVersionBinder(startAt = StackGresVersion.V09, stopAt = StackGresVersion.V10)
@OpJob("minorVersionUpgrade")
public class DbOpsMinorVersionUpgradeJob implements JobFactory {

  public static final String IMAGE_NAME = "docker.io/stackgres/jobs:%s";

  private final ResourceFactory<StackGresDbOpsContext, PodSecurityContext> podSecurityFactory;
  private final LabelFactoryForDbOps dbOpsLabelFactory;

  @Inject
  public DbOpsMinorVersionUpgradeJob(
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
    return new JobBuilder()
        .withNewMetadata()
        .withNamespace(namespace)
        .withName(jobName(dbOps, "minor-version-upgrade"))
        .withLabels(labels)
        .endMetadata()
        .withNewSpec()
        .withBackoffLimit(0)
        .withCompletions(1)
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
        .withContainers(new ContainerBuilder()
            .withName("minor-version-upgrade")
            .withImagePullPolicy("IfNotPresent")
            .withImage(String.format(IMAGE_NAME,
                StackGresProperty.OPERATOR_IMAGE_VERSION.getString()))
            .addToEnv(new EnvVarBuilder()
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
                    .withName("DATABASE_OPERATION_JOB")
                    .withValue(Boolean.TRUE.toString())
                    .build(),
                new EnvVarBuilder()
                    .withName("DATABASE_OPERATION_CR_NAME")
                    .withValue(dbOps.getMetadata().getName())
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
                    .withName("DEBUG_CLUSTER_CONTROLLER")
                    .withValue(System.getenv("DEBUG_JOBS"))
                    .build(),
                new EnvVarBuilder()
                    .withName("DEBUG_CLUSTER_CONTROLLER_SUSPEND")
                    .withValue(System.getenv("DEBUG_JOBS_SUSPEND"))
                    .build())
            .build())
        .endSpec()
        .endTemplate()
        .endSpec()
        .build();
  }

}
