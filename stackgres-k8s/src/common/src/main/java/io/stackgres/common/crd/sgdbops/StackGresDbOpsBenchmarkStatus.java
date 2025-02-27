/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.crd.sgdbops;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.stackgres.common.StackGresUtil;
import io.sundr.builder.annotations.Buildable;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@JsonIgnoreProperties(ignoreUnknown = true)
@Buildable(editableEnabled = false, validationEnabled = false, generateBuilderPackage = false,
    lazyCollectionInitEnabled = false, lazyMapInitEnabled = false,
    builderPackage = "io.fabric8.kubernetes.api.builder")
public class StackGresDbOpsBenchmarkStatus {

  private StackGresDbOpsPgbenchStatus pgbench;

  private StackGresDbOpsSamplingStatus sampling;

  public StackGresDbOpsPgbenchStatus getPgbench() {
    return pgbench;
  }

  public void setPgbench(StackGresDbOpsPgbenchStatus pgbench) {
    this.pgbench = pgbench;
  }

  public StackGresDbOpsSamplingStatus getSampling() {
    return sampling;
  }

  public void setSampling(StackGresDbOpsSamplingStatus sampling) {
    this.sampling = sampling;
  }

  @Override
  public int hashCode() {
    return Objects.hash(pgbench, sampling);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof StackGresDbOpsBenchmarkStatus)) {
      return false;
    }
    StackGresDbOpsBenchmarkStatus other = (StackGresDbOpsBenchmarkStatus) obj;
    return Objects.equals(pgbench, other.pgbench) && Objects.equals(sampling, other.sampling);
  }

  @Override
  public String toString() {
    return StackGresUtil.toPrettyYaml(this);
  }

}
