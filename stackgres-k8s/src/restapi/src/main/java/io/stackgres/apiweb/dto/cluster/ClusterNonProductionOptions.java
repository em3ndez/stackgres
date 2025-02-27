/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.dto.cluster;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.stackgres.common.StackGresUtil;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ClusterNonProductionOptions {

  Boolean disableClusterPodAntiAffinity;

  Boolean disablePatroniResourceRequirements;

  Boolean disableClusterResourceRequirements;

  Boolean enableSetPatroniCpuRequests;

  Boolean enableSetClusterCpuRequests;

  Boolean enableSetPatroniMemoryRequests;

  Boolean enableSetClusterMemoryRequests;

  List<String> enabledFeatureGates;

  public Boolean getDisableClusterPodAntiAffinity() {
    return disableClusterPodAntiAffinity;
  }

  public void setDisableClusterPodAntiAffinity(Boolean disableClusterPodAntiAffinity) {
    this.disableClusterPodAntiAffinity = disableClusterPodAntiAffinity;
  }

  public Boolean getDisablePatroniResourceRequirements() {
    return disablePatroniResourceRequirements;
  }

  public void setDisablePatroniResourceRequirements(Boolean disablePatroniResourceRequirements) {
    this.disablePatroniResourceRequirements = disablePatroniResourceRequirements;
  }

  public Boolean getDisableClusterResourceRequirements() {
    return disableClusterResourceRequirements;
  }

  public void setDisableClusterResourceRequirements(Boolean disableClusterResourceRequirements) {
    this.disableClusterResourceRequirements = disableClusterResourceRequirements;
  }

  public Boolean getEnableSetPatroniCpuRequests() {
    return enableSetPatroniCpuRequests;
  }

  public void setEnableSetPatroniCpuRequests(Boolean enableSetPatroniCpuRequests) {
    this.enableSetPatroniCpuRequests = enableSetPatroniCpuRequests;
  }

  public Boolean getEnableSetClusterCpuRequests() {
    return enableSetClusterCpuRequests;
  }

  public void setEnableSetClusterCpuRequests(Boolean enableSetClusterCpuRequests) {
    this.enableSetClusterCpuRequests = enableSetClusterCpuRequests;
  }

  public Boolean getEnableSetPatroniMemoryRequests() {
    return enableSetPatroniMemoryRequests;
  }

  public void setEnableSetPatroniMemoryRequests(Boolean enableSetPatroniMemoryRequests) {
    this.enableSetPatroniMemoryRequests = enableSetPatroniMemoryRequests;
  }

  public Boolean getEnableSetClusterMemoryRequests() {
    return enableSetClusterMemoryRequests;
  }

  public void setEnableSetClusterMemoryRequests(Boolean enableSetClusterMemoryRequests) {
    this.enableSetClusterMemoryRequests = enableSetClusterMemoryRequests;
  }

  public List<String> getEnabledFeatureGates() {
    return enabledFeatureGates;
  }

  public void setEnabledFeatureGates(List<String> enabledFeatureGates) {
    this.enabledFeatureGates = enabledFeatureGates;
  }

  @Override
  public String toString() {
    return StackGresUtil.toPrettyYaml(this);
  }

}
