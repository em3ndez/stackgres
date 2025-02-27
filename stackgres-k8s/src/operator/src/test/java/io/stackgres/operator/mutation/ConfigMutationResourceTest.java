/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.mutation;

import io.stackgres.common.crd.sgconfig.StackGresConfig;
import io.stackgres.operator.common.StackGresConfigReview;
import io.stackgres.operator.common.fixture.AdmissionReviewFixtures;
import io.stackgres.operatorframework.admissionwebhook.mutating.AbstractMutationResource;
import io.stackgres.testutil.JsonUtil;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfigMutationResourceTest extends MutationResourceTest<StackGresConfig, StackGresConfigReview> {

  @Override
  protected AbstractMutationResource<StackGresConfig, StackGresConfigReview> getResource() {
    return new ConfigMutationResource(JsonUtil.jsonMapper(), pipeline);
  }

  @Override
  protected StackGresConfigReview getReview() {
    return AdmissionReviewFixtures.config().loadCreate().get();
  }

}
