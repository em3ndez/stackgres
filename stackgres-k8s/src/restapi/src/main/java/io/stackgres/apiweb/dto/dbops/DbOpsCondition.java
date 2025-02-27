/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.dto.dbops;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.stackgres.common.StackGresUtil;
import io.stackgres.operatorframework.resource.Condition;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class DbOpsCondition implements Condition {

  private String lastTransitionTime;

  private String message;

  private String reason;

  private String status;

  private String type;

  /**
   * Constructor of the required fields.
   *
   * @param type of the condition
   * @param status of the condition, can be True, False, Unknown
   * @param reason of the condition
   */
  public DbOpsCondition(String type, String status, String reason) {
    this.type = type;
    this.status = status;
    this.reason = reason;
  }

  public DbOpsCondition() {
  }

  @Override
  public String getLastTransitionTime() {
    return lastTransitionTime;
  }

  @Override
  public void setLastTransitionTime(String lastTransitionTime) {
    this.lastTransitionTime = lastTransitionTime;
  }

  @Override
  public String getMessage() {
    return message;
  }

  @Override
  public void setMessage(String message) {
    this.message = message;
  }

  @Override
  public String getReason() {
    return reason;
  }

  @Override
  public void setReason(String reason) {
    this.reason = reason;
  }

  @Override
  public String getStatus() {
    return status;
  }

  @Override
  public void setStatus(String status) {
    this.status = status;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public void setType(String type) {
    this.type = type;
  }

  @Override
  public String toString() {
    return StackGresUtil.toPrettyYaml(this);
  }

}
