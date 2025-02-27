/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.rest;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.jboss.resteasy.plugins.interceptors.AcceptEncodingGZIPFilter;
import org.jboss.resteasy.plugins.interceptors.GZIPDecodingInterceptor;
import org.jboss.resteasy.plugins.interceptors.GZIPEncodingInterceptor;

@RegisterForReflection(targets = {
    AcceptEncodingGZIPFilter.class,
    GZIPDecodingInterceptor.class,
    GZIPEncodingInterceptor.class
}, ignoreNested = false)
public class RegisterGzipFilter {
}
