/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.dto.cluster;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.stackgres.common.StackGresUtil;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ClusterRestore {

  @JsonProperty("downloadDiskConcurrency")
  private Integer downloadDiskConcurrency;

  @JsonProperty("fromBackup")
  private ClusterRestoreFromBackup fromBackup;

  public Integer getDownloadDiskConcurrency() {
    return downloadDiskConcurrency;
  }

  public void setDownloadDiskConcurrency(Integer downloadDiskConcurrency) {
    this.downloadDiskConcurrency = downloadDiskConcurrency;
  }

  public ClusterRestoreFromBackup getFromBackup() {
    return fromBackup;
  }

  public void setFromBackup(ClusterRestoreFromBackup fromBackup) {
    this.fromBackup = fromBackup;
  }

  @Override
  public String toString() {
    return StackGresUtil.toPrettyYaml(this);
  }

}
