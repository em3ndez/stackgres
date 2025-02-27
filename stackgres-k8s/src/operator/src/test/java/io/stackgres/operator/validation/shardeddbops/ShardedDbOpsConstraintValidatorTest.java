/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation.shardeddbops;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.stackgres.common.crd.sgshardeddbops.ShardedDbOpsOperation;
import io.stackgres.common.crd.sgshardeddbops.ShardedDbOpsOperationAllowed;
import io.stackgres.common.crd.sgshardeddbops.StackGresShardedDbOps;
import io.stackgres.common.crd.sgshardeddbops.StackGresShardedDbOpsMajorVersionUpgrade;
import io.stackgres.common.crd.sgshardeddbops.StackGresShardedDbOpsMinorVersionUpgrade;
import io.stackgres.common.crd.sgshardeddbops.StackGresShardedDbOpsResharding;
import io.stackgres.common.crd.sgshardeddbops.StackGresShardedDbOpsRestart;
import io.stackgres.common.crd.sgshardeddbops.StackGresShardedDbOpsSecurityUpgrade;
import io.stackgres.common.crd.sgshardeddbops.StackGresShardedDbOpsSpec;
import io.stackgres.common.validation.ValidEnum;
import io.stackgres.operator.common.StackGresShardedDbOpsReview;
import io.stackgres.operator.common.fixture.AdmissionReviewFixtures;
import io.stackgres.operator.utils.ValidationUtils;
import io.stackgres.operator.validation.AbstractConstraintValidator;
import io.stackgres.operator.validation.ConstraintValidationTest;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ShardedDbOpsConstraintValidatorTest extends ConstraintValidationTest<StackGresShardedDbOpsReview> {

  @Override
  protected AbstractConstraintValidator<StackGresShardedDbOpsReview> buildValidator() {
    return new ShardedDbOpsConstraintValidator();
  }

  @Override
  protected StackGresShardedDbOpsReview getValidReview() {
    return AdmissionReviewFixtures.shardedDbOps().loadRestartCreate().get();
  }

  @Override
  protected StackGresShardedDbOpsReview getInvalidReview() {
    final StackGresShardedDbOpsReview review =
        AdmissionReviewFixtures.shardedDbOps().loadRestartCreate().get();

    review.getRequest().getObject().setSpec(null);
    return review;
  }

  @Test
  void nullSpec_shouldFail() {
    StackGresShardedDbOpsReview review = getValidReview();
    review.getRequest().getObject().setSpec(null);

    checkNotNullErrorCause(StackGresShardedDbOps.class, "spec", review);
  }

  @Test
  void nullSgCluster_shouldFail() {
    StackGresShardedDbOpsReview review = getValidReview();
    review.getRequest().getObject().getSpec().setSgShardedCluster(null);

    checkErrorCause(StackGresShardedDbOpsSpec.class, "spec.sgShardedCluster",
        review, NotEmpty.class);
  }

  @Test
  void nullOp_shouldFail() {

    StackGresShardedDbOpsReview review = getValidReview();
    review.getRequest().getObject().getSpec().setOp(null);

    checkErrorCause(StackGresShardedDbOpsSpec.class, "spec.op",
        review, ValidEnum.class);
  }

  @Test
  void wrongOp_shouldFail() {

    StackGresShardedDbOpsReview review = getValidReview();
    review.getRequest().getObject().getSpec().setOp("test");

    checkErrorCause(StackGresShardedDbOpsSpec.class, "spec.op",
        "spec.op", review, ValidEnum.class);
  }

  @Test
  void wrongRunAt_shouldFail() {

    StackGresShardedDbOpsReview review = getValidReview();
    review.getRequest().getObject().getSpec().setRunAt("2018-01-01 01:02:03");

    checkErrorCause(StackGresShardedDbOpsSpec.class, "spec.runAt",
        "spec.isRunAtValid", review, AssertTrue.class);
  }

  @Test
  void wrongTimeout_shouldFail() {

    StackGresShardedDbOpsReview review = getValidReview();
    review.getRequest().getObject().getSpec().setTimeout("10s");

    checkErrorCause(StackGresShardedDbOpsSpec.class, "spec.timeout",
        "spec.isTimeoutValid", review, AssertTrue.class);
  }

  @Test
  void negativeTimeout_shouldFail() {

    StackGresShardedDbOpsReview review = getValidReview();
    review.getRequest().getObject().getSpec().setTimeout("-PT1M");

    checkErrorCause(StackGresShardedDbOpsSpec.class, "spec.timeout",
        "spec.isTimeoutValid", review, AssertTrue.class);
  }

  @Test
  void invalidLowMaxRetries_shouldFail() {

    StackGresShardedDbOpsReview review = getValidReview();
    review.getRequest().getObject().getSpec().setMaxRetries(-1);

    checkErrorCause(StackGresShardedDbOpsSpec.class, "spec.maxRetries",
        review, Min.class);

  }

  @ParameterizedTest(name = "op: {0} section: {1}")
  @MethodSource("dbOpsOperationsMatrix")
  void opThatDontMatchSection_shouldFailWithMessage(
      ShardedDbOpsOperationAllowed op, ShardedDbOpsOperationAllowed section) {
    StackGresShardedDbOpsReview review = getValidReview();
    StackGresShardedDbOpsSpec spec = review.getRequest().getObject().getSpec();
    spec.setOp(op.toString());

    spec.setResharding(null);
    spec.setRestart(null);
    spec.setMajorVersionUpgrade(null);
    spec.setMinorVersionUpgrade(null);
    spec.setSecurityUpgrade(null);

    switch (ShardedDbOpsOperation.fromString(op.toString())) {
      case RESHARDING:
        spec.setResharding(new StackGresShardedDbOpsResharding());
        break;
      case RESTART:
        spec.setRestart(new StackGresShardedDbOpsRestart());
        break;
      case MAJOR_VERSION_UPGRADE:
        var major = new StackGresShardedDbOpsMajorVersionUpgrade();
        major.setPostgresVersion("14");
        major.setSgPostgresConfig("conf14");
        major.setBackupPaths(List.of("test"));
        spec.setMajorVersionUpgrade(major);
        break;
      case MINOR_VERSION_UPGRADE:
        var minor = new StackGresShardedDbOpsMinorVersionUpgrade();
        minor.setPostgresVersion("14.1");
        spec.setMinorVersionUpgrade(minor);
        break;
      case SECURITY_UPGRADE:
        spec.setSecurityUpgrade(new StackGresShardedDbOpsSecurityUpgrade());
        break;
      default:
        break;
    }

    switch (ShardedDbOpsOperation.fromString(section.toString())) {
      case RESHARDING:
        spec.setResharding(new StackGresShardedDbOpsResharding());
        break;
      case RESTART:
        spec.setRestart(new StackGresShardedDbOpsRestart());
        break;
      case MAJOR_VERSION_UPGRADE:
        var major = new StackGresShardedDbOpsMajorVersionUpgrade();
        major.setPostgresVersion("14");
        major.setSgPostgresConfig("conf14");
        major.setBackupPaths(List.of("test"));
        spec.setMajorVersionUpgrade(major);
        break;
      case MINOR_VERSION_UPGRADE:
        var minor = new StackGresShardedDbOpsMinorVersionUpgrade();
        minor.setPostgresVersion("14.1");
        spec.setMinorVersionUpgrade(minor);
        break;
      case SECURITY_UPGRADE:
        spec.setSecurityUpgrade(new StackGresShardedDbOpsSecurityUpgrade());
        break;
      default:
        break;
    }

    ValidationUtils.assertValidationFailed(() -> validator.validate(review),
        "SGShardedDbOps has invalid properties. op must match corresponding section.", 422);
  }

  private static Stream<Arguments> dbOpsOperationsMatrix() {
    return Arrays.stream(ShardedDbOpsOperationAllowed.values())
        .sorted()
        .collect(Collectors.toMap(Function.identity(),
            e -> Arrays.stream(ShardedDbOpsOperationAllowed.values())
                .filter(p -> p != e)
                .collect(Collectors.toUnmodifiableList())))
        .entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .flatMap(map -> map.getValue().stream()
            .sorted()
            .map(val -> Arguments.of(map.getKey(), val)));
  }

  @ParameterizedTest(name = "op: {0} section: {1}")
  @MethodSource("dbOpsOperationsSameMatrix")
  void opThatMatchSection_shouldNotFail(
      ShardedDbOpsOperationAllowed op, ShardedDbOpsOperationAllowed section) {
    StackGresShardedDbOpsReview review = getValidReview();
    StackGresShardedDbOpsSpec spec = review.getRequest().getObject().getSpec();
    spec.setOp(op.toString());

    spec.setResharding(null);
    spec.setRestart(null);
    spec.setMajorVersionUpgrade(null);
    spec.setMinorVersionUpgrade(null);
    spec.setSecurityUpgrade(null);

    switch (ShardedDbOpsOperation.fromString(section.toString())) {
      case RESHARDING:
        spec.setResharding(new StackGresShardedDbOpsResharding());
        break;
      case RESTART:
        spec.setRestart(new StackGresShardedDbOpsRestart());
        break;
      case MAJOR_VERSION_UPGRADE:
        var major = new StackGresShardedDbOpsMajorVersionUpgrade();
        major.setPostgresVersion("14");
        major.setSgPostgresConfig("conf14");
        major.setBackupPaths(List.of("test"));
        spec.setMajorVersionUpgrade(major);
        break;
      case MINOR_VERSION_UPGRADE:
        var minor = new StackGresShardedDbOpsMinorVersionUpgrade();
        minor.setPostgresVersion("14.1");
        spec.setMinorVersionUpgrade(minor);
        break;
      case SECURITY_UPGRADE:
        spec.setSecurityUpgrade(new StackGresShardedDbOpsSecurityUpgrade());
        break;
      default:
        break;
    }
    assertDoesNotThrow(() -> validator.validate(review));
  }

  private static Stream<Arguments> dbOpsOperationsSameMatrix() {
    return Arrays.stream(ShardedDbOpsOperationAllowed.values())
        .sorted()
        .map(val -> Arguments.of(val, val));
  }

}
