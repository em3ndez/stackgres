/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.script;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.stackgres.common.crd.sgscript.StackGresScript;
import io.stackgres.operator.conciliation.AbstractReconciliationHandler;
import io.stackgres.operator.conciliation.ReconciliationScope;

@ReconciliationScope(value = StackGresScript.class, kind = "HasMetadata")
@ApplicationScoped
public class ScriptDefaultReconciliationHandler
    extends AbstractReconciliationHandler<StackGresScript> {

  @Inject
  public ScriptDefaultReconciliationHandler(KubernetesClient client) {
    super(client);
  }

}
