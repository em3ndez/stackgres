/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.dto.dbops;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.stackgres.common.StackGresUtil;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class DbOpsRestartStatus {

  private String primaryInstance;

  private List<String> initialInstances;

  private List<String> pendingToRestartInstances;

  private List<String> restartedInstances;

  private String switchoverInitiated;

  private String switchoverFinalized;

  private String failure;

  public String getPrimaryInstance() {
    return primaryInstance;
  }

  public void setPrimaryInstance(String primaryInstance) {
    this.primaryInstance = primaryInstance;
  }

  public List<String> getInitialInstances() {
    return initialInstances;
  }

  public void setInitialInstances(List<String> initialInstances) {
    this.initialInstances = initialInstances;
  }

  public List<String> getPendingToRestartInstances() {
    return pendingToRestartInstances;
  }

  public void setPendingToRestartInstances(List<String> pendingToRestartInstances) {
    this.pendingToRestartInstances = pendingToRestartInstances;
  }

  public List<String> getRestartedInstances() {
    return restartedInstances;
  }

  public void setRestartedInstances(List<String> restartedInstances) {
    this.restartedInstances = restartedInstances;
  }

  public String getSwitchoverInitiated() {
    return switchoverInitiated;
  }

  public void setSwitchoverInitiated(String switchoverInitiated) {
    this.switchoverInitiated = switchoverInitiated;
  }

  public String getSwitchoverFinalized() {
    return switchoverFinalized;
  }

  public void setSwitchoverFinalized(String switchoverFinalized) {
    this.switchoverFinalized = switchoverFinalized;
  }

  public String getFailure() {
    return failure;
  }

  public void setFailure(String failure) {
    this.failure = failure;
  }

  @Override
  public String toString() {
    return StackGresUtil.toPrettyYaml(this);
  }

}
