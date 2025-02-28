/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.resource;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.stackgres.common.crd.sgscript.StackGresScript;
import io.stackgres.common.crd.sgscript.StackGresScriptList;

@ApplicationScoped
public class ScriptScanner
    extends AbstractCustomResourceScanner<StackGresScript, StackGresScriptList> {

  /**
   * Create a {@code ScriptScanner} instance.
   */
  @Inject
  public ScriptScanner(KubernetesClient client) {
    super(client, StackGresScript.class, StackGresScriptList.class);
  }

}
