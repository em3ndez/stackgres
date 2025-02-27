/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.dto.cluster;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.stackgres.common.StackGresUtil;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ClusterRestoreFromBackup {

  private String uid;

  private String name;

  private ClusterRestorePitr pointInTimeRecovery;

  public String getUid() {
    return uid;
  }

  public void setUid(String uid) {
    this.uid = uid;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public ClusterRestorePitr getPointInTimeRecovery() {
    return pointInTimeRecovery;
  }

  public void setPointInTimeRecovery(ClusterRestorePitr pointInTimeRecovery) {
    this.pointInTimeRecovery = pointInTimeRecovery;
  }

  @Override
  public String toString() {
    return StackGresUtil.toPrettyYaml(this);
  }

}
