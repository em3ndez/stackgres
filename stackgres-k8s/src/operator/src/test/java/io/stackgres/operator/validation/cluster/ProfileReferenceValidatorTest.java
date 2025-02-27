/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import io.stackgres.common.crd.sgprofile.StackGresProfile;
import io.stackgres.common.fixture.Fixtures;
import io.stackgres.common.resource.AbstractCustomResourceFinder;
import io.stackgres.operator.common.StackGresClusterReview;
import io.stackgres.operator.common.fixture.AdmissionReviewFixtures;
import io.stackgres.operatorframework.admissionwebhook.Operation;
import io.stackgres.operatorframework.admissionwebhook.validating.ValidationFailed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@RunWith(MockitoJUnitRunner.class)
class ProfileReferenceValidatorTest {

  private ProfileReferenceValidator validator;

  @Mock
  private AbstractCustomResourceFinder<StackGresProfile> profileFinder;

  private StackGresProfile profileSizeXs;

  @BeforeEach
  void setUp() throws Exception {
    validator = new ProfileReferenceValidator(profileFinder);

    profileSizeXs = Fixtures.instanceProfile().loadSizeS().get();

  }

  @Test
  void givenValidStackGresReferenceOnCreation_shouldNotFail() throws ValidationFailed {

    final StackGresClusterReview review = AdmissionReviewFixtures.cluster().loadCreate().get();

    String resourceProfile = review.getRequest().getObject().getSpec().getSgInstanceProfile();
    String namespace = review.getRequest().getObject().getMetadata().getNamespace();

    when(profileFinder.findByNameAndNamespace(resourceProfile, namespace))
        .thenReturn(Optional.of(profileSizeXs));

    validator.validate(review);

    verify(profileFinder).findByNameAndNamespace(eq(resourceProfile), eq(namespace));

  }

  @Test
  void giveInvalidStackGresReferenceOnCreation_shouldFail() {

    final StackGresClusterReview review = AdmissionReviewFixtures.cluster().loadCreate().get();

    String resourceProfile = review.getRequest().getObject().getSpec().getSgInstanceProfile();
    String namespace = review.getRequest().getObject().getMetadata().getNamespace();

    when(profileFinder.findByNameAndNamespace(resourceProfile, namespace))
        .thenReturn(Optional.empty());

    ValidationFailed ex = assertThrows(ValidationFailed.class, () -> {
      validator.validate(review);
    });

    String resultMessage = ex.getMessage();

    assertEquals("SGInstanceProfile " + resourceProfile + " not found", resultMessage);

    verify(profileFinder).findByNameAndNamespace(anyString(), anyString());
  }

  @Test
  void giveAnAttemptToUpdateToAnUnknownProfile_shouldFail() {

    final StackGresClusterReview review = AdmissionReviewFixtures.cluster()
        .loadProfileConfigUpdate().get();

    String resourceProfile = review.getRequest().getObject().getSpec().getSgInstanceProfile();
    String namespace = review.getRequest().getObject().getMetadata().getNamespace();

    when(profileFinder.findByNameAndNamespace(resourceProfile, namespace))
        .thenReturn(Optional.empty());

    ValidationFailed ex = assertThrows(ValidationFailed.class, () -> {
      validator.validate(review);
    });

    String resultMessage = ex.getMessage();

    assertEquals("Cannot update to SGInstanceProfile " + resourceProfile
        + " because it doesn't exists", resultMessage);

    verify(profileFinder).findByNameAndNamespace(anyString(), anyString());

  }

  @Test
  void giveAnAttemptToUpdateToAnKnownProfile_shouldNotFail() throws ValidationFailed {

    final StackGresClusterReview review = AdmissionReviewFixtures.cluster()
        .loadProfileConfigUpdate().get();

    String resourceProfile = review.getRequest().getObject().getSpec().getSgInstanceProfile();
    String namespace = review.getRequest().getObject().getMetadata().getNamespace();

    StackGresProfile profileSizeS = Fixtures.instanceProfile().loadSizeM().get();

    when(profileFinder.findByNameAndNamespace(resourceProfile, namespace))
        .thenReturn(Optional.of(profileSizeS));

    validator.validate(review);

    verify(profileFinder).findByNameAndNamespace(anyString(), anyString());

  }

  @Test
  void giveAnAttemptToDelete_shouldNotFail() throws ValidationFailed {

    final StackGresClusterReview review = AdmissionReviewFixtures.cluster()
        .loadProfileConfigUpdate().get();
    review.getRequest().setOperation(Operation.DELETE);

    validator.validate(review);

    verify(profileFinder, never()).findByNameAndNamespace(anyString(), anyString());

  }

}
