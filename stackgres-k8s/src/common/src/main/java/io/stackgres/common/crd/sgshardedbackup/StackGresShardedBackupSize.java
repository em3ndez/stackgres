/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.crd.sgshardedbackup;

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
public class StackGresShardedBackupSize {

  private Long uncompressed;
  private Long compressed;

  public Long getUncompressed() {
    return uncompressed;
  }

  public void setUncompressed(Long uncompressed) {
    this.uncompressed = uncompressed;
  }

  public Long getCompressed() {
    return compressed;
  }

  public void setCompressed(Long compressed) {
    this.compressed = compressed;
  }

  @Override
  public int hashCode() {
    return Objects.hash(compressed, uncompressed);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof StackGresShardedBackupSize)) {
      return false;
    }
    StackGresShardedBackupSize other = (StackGresShardedBackupSize) obj;
    return Objects.equals(compressed, other.compressed)
        && Objects.equals(uncompressed, other.uncompressed);
  }

  @Override
  public String toString() {
    return StackGresUtil.toPrettyYaml(this);
  }
}
