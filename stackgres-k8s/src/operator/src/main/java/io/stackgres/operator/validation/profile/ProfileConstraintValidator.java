/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation.profile;

import io.stackgres.common.ErrorType;
import io.stackgres.operator.common.StackGresInstanceProfileReview;
import io.stackgres.operator.validation.AbstractConstraintValidator;
import io.stackgres.operator.validation.ValidationType;
import jakarta.inject.Singleton;

@Singleton
@ValidationType(ErrorType.CONSTRAINT_VIOLATION)
public class ProfileConstraintValidator
    extends AbstractConstraintValidator<StackGresInstanceProfileReview>
    implements SgProfileValidator {
}
