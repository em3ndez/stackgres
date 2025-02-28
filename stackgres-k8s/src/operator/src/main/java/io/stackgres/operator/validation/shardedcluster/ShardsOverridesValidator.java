/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation.shardedcluster;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Singleton;

import io.stackgres.common.ErrorType;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterShard;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterShards;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterSpec;
import io.stackgres.operator.common.StackGresShardedClusterReview;
import io.stackgres.operator.validation.ValidationType;
import io.stackgres.operatorframework.admissionwebhook.Operation;
import io.stackgres.operatorframework.admissionwebhook.validating.ValidationFailed;

@Singleton
@ValidationType(ErrorType.CONSTRAINT_VIOLATION)
public class ShardsOverridesValidator implements ShardedClusterValidator {

  private final String constraintViolationUri = ErrorType
      .getErrorTypeUri(ErrorType.CONSTRAINT_VIOLATION);

  @Override
  public void validate(StackGresShardedClusterReview review) throws ValidationFailed {
    StackGresShardedCluster cluster = review.getRequest().getObject();
    if (review.getRequest().getOperation() == Operation.UPDATE
        || review.getRequest().getOperation() == Operation.CREATE) {
      var overridesShards = Optional.of(cluster.getSpec())
          .map(StackGresShardedClusterSpec::getShards)
          .map(StackGresShardedClusterShards::getOverrides)
          .orElse(List.of());
      checkIndexesUniqueness(overridesShards);
    }
  }

  private void checkIndexesUniqueness(
      List<StackGresShardedClusterShard> overridesShards) throws ValidationFailed {
    if (overridesShards.stream()
        .collect(Collectors.groupingBy(StackGresShardedClusterShard::getIndex))
        .values()
        .stream()
        .anyMatch(list -> list.size() > 1)) {
      fail(constraintViolationUri, "Shards overrides must contain unique indexes");
    }
  }

}
