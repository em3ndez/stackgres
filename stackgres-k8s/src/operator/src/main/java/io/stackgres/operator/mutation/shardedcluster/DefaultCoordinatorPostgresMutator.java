/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.mutation.shardedcluster;

import io.stackgres.common.crd.sgcluster.StackGresClusterConfigurations;
import io.stackgres.common.crd.sgpgconfig.StackGresPostgresConfig;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterCoordinator;
import io.stackgres.common.resource.CustomResourceFinder;
import io.stackgres.common.resource.CustomResourceScheduler;
import io.stackgres.operator.common.StackGresShardedClusterReview;
import io.stackgres.operator.initialization.DefaultCustomResourceFactory;
import io.stackgres.operator.mutation.AbstractDefaultResourceMutator;

public class DefaultCoordinatorPostgresMutator
    extends AbstractDefaultResourceMutator<
        StackGresPostgresConfig, StackGresShardedCluster, StackGresShardedClusterReview> {

  public DefaultCoordinatorPostgresMutator(
      DefaultCustomResourceFactory<StackGresPostgresConfig> resourceFactory,
      CustomResourceFinder<StackGresPostgresConfig> finder,
      CustomResourceScheduler<StackGresPostgresConfig> scheduler) {
    super(resourceFactory, finder, scheduler);
  }

  @Override
  protected void setValueSection(StackGresShardedCluster resource) {
    if (resource.getSpec().getCoordinator() == null) {
      resource.getSpec().setCoordinator(
          new StackGresShardedClusterCoordinator());
    }
    if (resource.getSpec().getCoordinator().getConfigurations() == null) {
      resource.getSpec().getCoordinator().setConfigurations(
          new StackGresClusterConfigurations());
    }
  }

  @Override
  protected String getTargetPropertyValue(StackGresShardedCluster resource) {
    return resource.getSpec().getCoordinator().getConfigurations().getSgPostgresConfig();
  }

  @Override
  protected void setTargetProperty(StackGresShardedCluster resource, String defaultResourceName) {
    resource.getSpec().getCoordinator().getConfigurations()
        .setSgPostgresConfig(defaultResourceName);
  }

}
