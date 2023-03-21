/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.quarkus.runtime.StartupEvent;
import io.stackgres.operator.common.StackGresClusterReview;
import io.stackgres.operatorframework.admissionwebhook.AdmissionReviewResponse;
import io.stackgres.operatorframework.admissionwebhook.validating.ValidationPipeline;
import io.stackgres.operatorframework.admissionwebhook.validating.ValidationResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(ValidationUtil.CLUSTER_VALIDATION_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ClusterValidationResource implements ValidationResource<StackGresClusterReview> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClusterValidationResource.class);

  private ValidationPipeline<StackGresClusterReview> pipeline;

  void onStart(@Observes StartupEvent ev) {
    LOGGER.info("Cluster validation resource started");
  }

  /**
   * Admission Web hook callback.
   */
  @POST
  public AdmissionReviewResponse validate(StackGresClusterReview admissionReview) {

    return validate(admissionReview, pipeline);

  }

  @Inject
  public void setPipeline(ValidationPipeline<StackGresClusterReview> pipeline) {
    this.pipeline = pipeline;
  }
}
