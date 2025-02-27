/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.crd.sgstream;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.stackgres.common.StackGresUtil;
import io.stackgres.common.crd.SecretKeySelector;
import io.sundr.builder.annotations.Buildable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@JsonIgnoreProperties(ignoreUnknown = true)
@Buildable(editableEnabled = false, validationEnabled = false, generateBuilderPackage = false,
    lazyCollectionInitEnabled = false, lazyMapInitEnabled = false,
    builderPackage = "io.fabric8.kubernetes.api.builder")
public class StackGresStreamTargetSgCluster {

  @NotNull
  private String name;

  private String database;

  @Valid
  private SecretKeySelector username;

  @Valid
  private SecretKeySelector password;

  private Boolean skipDdlImport;

  private String ddlImportRoleSkipFilter;

  @Valid
  private StackGresStreamTargetJdbcSinkDebeziumProperties debeziumProperties;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDatabase() {
    return database;
  }

  public void setDatabase(String database) {
    this.database = database;
  }

  public SecretKeySelector getUsername() {
    return username;
  }

  public void setUsername(SecretKeySelector username) {
    this.username = username;
  }

  public SecretKeySelector getPassword() {
    return password;
  }

  public void setPassword(SecretKeySelector password) {
    this.password = password;
  }

  public Boolean getSkipDdlImport() {
    return skipDdlImport;
  }

  public void setSkipDdlImport(Boolean skipDdlImport) {
    this.skipDdlImport = skipDdlImport;
  }

  public String getDdlImportRoleSkipFilter() {
    return ddlImportRoleSkipFilter;
  }

  public void setDdlImportRoleSkipFilter(String ddlImportRoleSkipFilter) {
    this.ddlImportRoleSkipFilter = ddlImportRoleSkipFilter;
  }

  public StackGresStreamTargetJdbcSinkDebeziumProperties getDebeziumProperties() {
    return debeziumProperties;
  }

  public void setDebeziumProperties(
      StackGresStreamTargetJdbcSinkDebeziumProperties debeziumProperties) {
    this.debeziumProperties = debeziumProperties;
  }

  @Override
  public int hashCode() {
    return Objects.hash(database, ddlImportRoleSkipFilter, debeziumProperties, name, password,
        skipDdlImport, username);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof StackGresStreamTargetSgCluster)) {
      return false;
    }
    StackGresStreamTargetSgCluster other = (StackGresStreamTargetSgCluster) obj;
    return Objects.equals(database, other.database)
        && Objects.equals(ddlImportRoleSkipFilter, other.ddlImportRoleSkipFilter)
        && Objects.equals(debeziumProperties, other.debeziumProperties)
        && Objects.equals(name, other.name) && Objects.equals(password, other.password)
        && Objects.equals(skipDdlImport, other.skipDdlImport)
        && Objects.equals(username, other.username);
  }

  @Override
  public String toString() {
    return StackGresUtil.toPrettyYaml(this);
  }

}
