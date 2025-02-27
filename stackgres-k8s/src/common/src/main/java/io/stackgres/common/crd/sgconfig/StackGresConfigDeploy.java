/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.crd.sgconfig;

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
public class StackGresConfigDeploy {

  private Boolean restapi;

  private Boolean collector;

  public Boolean getRestapi() {
    return restapi;
  }

  public void setRestapi(Boolean restapi) {
    this.restapi = restapi;
  }

  public Boolean getCollector() {
    return collector;
  }

  public void setCollector(Boolean collector) {
    this.collector = collector;
  }

  @Override
  public int hashCode() {
    return Objects.hash(collector, restapi);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof StackGresConfigDeploy)) {
      return false;
    }
    StackGresConfigDeploy other = (StackGresConfigDeploy) obj;
    return Objects.equals(collector, other.collector) && Objects.equals(restapi, other.restapi);
  }

  @Override
  public String toString() {
    return StackGresUtil.toPrettyYaml(this);
  }

}
