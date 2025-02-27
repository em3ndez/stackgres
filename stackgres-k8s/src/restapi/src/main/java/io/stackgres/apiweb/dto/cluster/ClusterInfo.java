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
public final class ClusterInfo {

  private String primaryDns;

  private String replicasDns;

  private String superuserUsername;

  private String superuserSecretName;

  private String superuserUsernameKey;

  private String superuserPasswordKey;

  public String getPrimaryDns() {
    return primaryDns;
  }

  public void setPrimaryDns(String primaryDns) {
    this.primaryDns = primaryDns;
  }

  public String getReplicasDns() {
    return replicasDns;
  }

  public void setReplicasDns(String replicasDns) {
    this.replicasDns = replicasDns;
  }

  public String getSuperuserUsername() {
    return superuserUsername;
  }

  public void setSuperuserUsername(String superuserUsername) {
    this.superuserUsername = superuserUsername;
  }

  public String getSuperuserSecretName() {
    return superuserSecretName;
  }

  public void setSuperuserSecretName(String superuserSecretName) {
    this.superuserSecretName = superuserSecretName;
  }

  public String getSuperuserUsernameKey() {
    return superuserUsernameKey;
  }

  public void setSuperuserUsernameKey(String superuserUsernameKey) {
    this.superuserUsernameKey = superuserUsernameKey;
  }

  public String getSuperuserPasswordKey() {
    return superuserPasswordKey;
  }

  public void setSuperuserPasswordKey(String superuserPasswordKey) {
    this.superuserPasswordKey = superuserPasswordKey;
  }

  @Override
  public String toString() {
    return StackGresUtil.toPrettyYaml(this);
  }

}
