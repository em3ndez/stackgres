/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.stream.jobs;

import java.util.Objects;

import io.stackgres.common.crd.sgstream.StreamTargetType;
import jakarta.enterprise.util.AnnotationLiteral;

public class StreamTargetOperationLiteral extends AnnotationLiteral<StreamTargetOperation>
    implements StreamTargetOperation {

  private static final long serialVersionUID = 1L;

  private final StreamTargetType value;

  public StreamTargetOperationLiteral(StreamTargetType value) {
    this.value = value;
  }

  @Override
  public StreamTargetType value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    StreamTargetOperationLiteral that = (StreamTargetOperationLiteral) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), value);
  }
}
