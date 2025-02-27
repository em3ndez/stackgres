/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import io.fabric8.kubernetes.client.CustomResource;
import io.stackgres.common.ErrorType;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgcluster.StackGresClusterList;
import io.stackgres.common.fixture.Fixtures;
import io.stackgres.common.resource.CustomResourceScanner;
import io.stackgres.operator.utils.ValidationUtils;
import io.stackgres.operatorframework.admissionwebhook.AdmissionReview;
import io.stackgres.operatorframework.admissionwebhook.validating.ValidationFailed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public abstract class DependenciesValidatorTest
    <T extends AdmissionReview<?>,
    V extends DependenciesValidator<T, StackGresCluster>> {

  protected V validator;

  @Mock
  protected CustomResourceScanner<StackGresCluster> resourceScanner;

  protected abstract V setUpValidation();

  @BeforeEach
  void setUp() {
    validator = setUpValidation();
    validator.setResourceScanner(resourceScanner);
  }

  @Test
  public void givenAReviewCreation_itShouldDoNothing() throws ValidationFailed {
    T review = getReview_givenAReviewCreation_itShouldDoNothing();

    validator.validate(review);

    verify(resourceScanner, never()).findResources();
    verify(resourceScanner, never()).findResources(anyString());
  }

  protected abstract T getReview_givenAReviewCreation_itShouldDoNothing();

  @Test
  public void givenAReviewUpdate_itShouldDoNothing() throws ValidationFailed {
    T review = getReview_givenAReviewUpdate_itShouldDoNothing();

    validator.validate(review);

    verify(resourceScanner, never()).findResources();
    verify(resourceScanner, never()).findResources(anyString());
  }

  protected abstract T getReview_givenAReviewUpdate_itShouldDoNothing();

  @Test
  public void givenAReviewDelete_itShouldFailIfAClusterDependsOnIt() {
    T review = getReview_givenAReviewDelete_itShouldFailIfAClusterDependsOnIt();

    StackGresClusterList clusterList = Fixtures.clusterList().loadDefault().get();
    clusterList
        .getItems()
        .stream()
        .forEach(cluster -> makeClusterDependant(cluster, review));

    when(resourceScanner.findResources(review.getRequest().getNamespace()))
        .thenReturn(Optional.of(clusterList.getItems()));

    ValidationFailed ex = ValidationUtils.assertErrorType(ErrorType.FORBIDDEN_CR_DELETION,
        () -> validator.validate(review));

    assertEquals("Can't delete "
        + review.getRequest().getResource().getResource()
        + "." + review.getRequest().getKind().getGroup()
        + " " + review.getRequest().getName() + " because the "
        + CustomResource.getCRDName(StackGresCluster.class) + " "
        + clusterList.getItems().get(0).getMetadata().getName() + " depends on it",
        ex.getResult().getMessage());
  }

  protected abstract T getReview_givenAReviewDelete_itShouldFailIfAClusterDependsOnIt();

  protected abstract void makeClusterDependant(StackGresCluster cluster, T review);

  @Test
  public void givenAReviewDelete_itShouldNotFailIfNoClusterDependsOnIt() throws ValidationFailed {
    T review = getReview_givenAReviewDelete_itShouldNotFailIfNoClusterDependsOnIt();

    StackGresClusterList clusterList = Fixtures.clusterList().loadDefault().get();
    clusterList
        .getItems()
        .stream()
        .forEach(this::makeClusterNotDependant);

    when(resourceScanner.findResources(review.getRequest().getNamespace()))
        .thenReturn(Optional.of(clusterList.getItems()));

    validator.validate(review);

    verify(resourceScanner, never()).findResources();
    verify(resourceScanner).findResources(review.getRequest().getNamespace());
  }

  protected abstract T getReview_givenAReviewDelete_itShouldNotFailIfNoClusterDependsOnIt()
      throws ValidationFailed;

  protected abstract void makeClusterNotDependant(StackGresCluster cluster);

  @Test
  public void givenAReviewDelete_itShouldNotFailIfNoClusterExists() throws ValidationFailed {
    T review = getReview_givenAReviewDelete_itShouldNotFailIfNoClusterExists();

    when(resourceScanner.findResources(review.getRequest().getNamespace()))
        .thenReturn(Optional.empty());

    validator.validate(review);

    verify(resourceScanner, never()).findResources();
    verify(resourceScanner).findResources(review.getRequest().getNamespace());
  }

  protected abstract T getReview_givenAReviewDelete_itShouldNotFailIfNoClusterExists();

}
