/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.event;

import javax.enterprise.context.ApplicationScoped;

import io.stackgres.common.crd.sgdbops.StackGresDbOps;

@ApplicationScoped
public class DbOpsEventEmitter extends AbstractEventEmitter<StackGresDbOps> {

}
