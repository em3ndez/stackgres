/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation.config;

import io.stackgres.common.ErrorType;
import io.stackgres.operator.common.StackGresConfigReview;
import io.stackgres.operator.validation.AbstractConstraintValidator;
import io.stackgres.operator.validation.ValidationType;
import jakarta.inject.Singleton;

@Singleton
@ValidationType(ErrorType.CONSTRAINT_VIOLATION)
public class ConfigConstraintValidation extends AbstractConstraintValidator<StackGresConfigReview>
    implements ConfigValidator {

}
