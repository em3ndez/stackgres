/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.config;

import java.util.List;
import java.util.Optional;

import io.stackgres.common.StackGresProperty;
import io.stackgres.common.crd.Condition;
import io.stackgres.common.crd.sgconfig.StackGresConfig;
import io.stackgres.common.crd.sgconfig.StackGresConfigStatus;
import io.stackgres.operator.conciliation.StatusManager;
import io.stackgres.operatorframework.resource.ConditionUpdater;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ConfigStatusManager
    extends ConditionUpdater<StackGresConfig, Condition>
    implements StatusManager<StackGresConfig, Condition> {

  @Override
  public StackGresConfig refreshCondition(StackGresConfig source) {
    if (source.getStatus() == null) {
      source.setStatus(new StackGresConfigStatus());
    }
    source.getStatus().setVersion(StackGresProperty.OPERATOR_VERSION.getString());
    return source;
  }

  @Override
  protected List<Condition> getConditions(StackGresConfig context) {
    return Optional.ofNullable(context.getStatus())
        .map(StackGresConfigStatus::getConditions)
        .orElse(List.of());
  }

  @Override
  protected void setConditions(
      StackGresConfig source,
      List<Condition> conditions) {
    if (source.getStatus() == null) {
      source.setStatus(new StackGresConfigStatus());
    }
    source.getStatus().setConditions(conditions);
  }

}
