/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.dto.dbops;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.stackgres.common.StackGresUtil;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class DbOpsBenchmark {

  private String type;

  private String database;

  private DbOpsBenchmarkCredentials credentials;

  private DbOpsPgbench pgbench;

  private DbOpsSampling sampling;

  private String connectionType;

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getDatabase() {
    return database;
  }

  public void setDatabase(String database) {
    this.database = database;
  }

  public DbOpsBenchmarkCredentials getCredentials() {
    return credentials;
  }

  public void setCredentials(DbOpsBenchmarkCredentials credentials) {
    this.credentials = credentials;
  }

  public DbOpsPgbench getPgbench() {
    return pgbench;
  }

  public void setPgbench(DbOpsPgbench pgbench) {
    this.pgbench = pgbench;
  }

  public DbOpsSampling getSampling() {
    return sampling;
  }

  public void setSampling(DbOpsSampling sampling) {
    this.sampling = sampling;
  }

  public String getConnectionType() {
    return connectionType;
  }

  public void setConnectionType(String connectionType) {
    this.connectionType = connectionType;
  }

  @Override
  public String toString() {
    return StackGresUtil.toPrettyYaml(this);
  }

}
