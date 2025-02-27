/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation.pooling;

import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.operator.common.StackGresPoolingConfigReview;
import io.stackgres.operator.common.fixture.AdmissionReviewFixtures;
import io.stackgres.operator.validation.DependenciesValidatorTest;
import io.stackgres.operatorframework.admissionwebhook.validating.ValidationFailed;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@RunWith(MockitoJUnitRunner.class)
class PoolingDependenciesValidatorTest
    extends DependenciesValidatorTest<StackGresPoolingConfigReview, PoolingDependenciesValidator> {

  @Override
  protected PoolingDependenciesValidator setUpValidation() {
    return new PoolingDependenciesValidator();
  }

  @Override
  protected StackGresPoolingConfigReview getReview_givenAReviewCreation_itShouldDoNothing() {
    return AdmissionReviewFixtures.poolingConfig().loadCreate().get();
  }

  @Override
  protected StackGresPoolingConfigReview getReview_givenAReviewUpdate_itShouldDoNothing() {
    return AdmissionReviewFixtures.poolingConfig().loadUpdate().get();
  }

  @Override
  protected StackGresPoolingConfigReview getReview_givenAReviewDelete_itShouldFailIfAClusterDependsOnIt() {
    return AdmissionReviewFixtures.poolingConfig().loadDelete().get();
  }

  @Override
  protected StackGresPoolingConfigReview getReview_givenAReviewDelete_itShouldNotFailIfNoClusterDependsOnIt()
      throws ValidationFailed {
    return AdmissionReviewFixtures.poolingConfig().loadDelete().get();
  }

  @Override
  protected StackGresPoolingConfigReview getReview_givenAReviewDelete_itShouldNotFailIfNoClusterExists() {
    return AdmissionReviewFixtures.poolingConfig().loadDelete().get();
  }

  @Override
  protected void makeClusterDependant(StackGresCluster cluster, StackGresPoolingConfigReview review) {
    cluster.getSpec().getConfigurations().setSgPoolingConfig(review.getRequest().getName());
  }

  @Override
  protected void makeClusterNotDependant(StackGresCluster cluster) {
    cluster.getSpec().getConfigurations().setSgPoolingConfig(null);
  }
}
