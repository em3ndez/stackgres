/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.mutation.backup;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import io.stackgres.operator.common.BackupReview;
import io.stackgres.operatorframework.admissionwebhook.mutating.JsonPatchMutator;

public interface BackupMutator  extends JsonPatchMutator<BackupReview> {

  JsonPointer STATUS_POINTER = JsonPointer.of("status");

  static String getJsonMappingField(String field, Class<?> clazz) throws NoSuchFieldException {
    return clazz.getDeclaredField(field)
        .getAnnotation(JsonProperty.class)
        .value();
  }

}
