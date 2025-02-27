/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.factory.shardeddbops;

import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.operator.conciliation.OperatorVersionBinder;
import io.stackgres.operator.conciliation.factory.AbstractShardedClusterAnnotationDecorator;
import io.stackgres.operator.conciliation.shardeddbops.StackGresShardedDbOpsContext;
import jakarta.inject.Singleton;

@Singleton
@OperatorVersionBinder
public class ShardedDbOpsAnnotationDecorator
    extends AbstractShardedClusterAnnotationDecorator<StackGresShardedDbOpsContext> {

  @Override
  protected StackGresShardedCluster getShardedCluster(StackGresShardedDbOpsContext context) {
    return context.getShardedCluster();
  }

}
