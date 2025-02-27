/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.crd.sgshardedcluster;

import org.jetbrains.annotations.NotNull;

public enum StackGresShardingSphereModeType {

  STANDALONE("Standalone"),
  CLUSTER("Cluster");

  private final @NotNull String type;

  StackGresShardingSphereModeType(@NotNull String type) {
    this.type = type;
  }

  @Override
  public @NotNull String toString() {
    return type;
  }

  public static @NotNull StackGresShardingSphereModeType fromString(@NotNull String value) {
    for (StackGresShardingSphereModeType type : StackGresShardingSphereModeType.values()) {
      if (type.toString().equals(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknwon sharding type " + value);
  }
}
