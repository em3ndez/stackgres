/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.dto.stream;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.stackgres.common.StackGresUtil;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class StreamDebeziumEngineProperties {

  private String offsetCommitPolicy;

  private Integer offsetFlushIntervalMs;

  private Integer offsetFlushTimeoutMs;

  private Integer errorsMaxRetries;

  private Integer errorsRetryDelayInitialMs;

  private Integer errorsRetryDelayMaxMs;

  private Map<String, Map<String, String>> transforms;

  private Map<String, Map<String, String>> predicates;

  public String getOffsetCommitPolicy() {
    return offsetCommitPolicy;
  }

  public void setOffsetCommitPolicy(String offsetCommitPolicy) {
    this.offsetCommitPolicy = offsetCommitPolicy;
  }

  public Integer getOffsetFlushIntervalMs() {
    return offsetFlushIntervalMs;
  }

  public void setOffsetFlushIntervalMs(Integer offsetFlushIntervalMs) {
    this.offsetFlushIntervalMs = offsetFlushIntervalMs;
  }

  public Integer getOffsetFlushTimeoutMs() {
    return offsetFlushTimeoutMs;
  }

  public void setOffsetFlushTimeoutMs(Integer offsetFlushTimeoutMs) {
    this.offsetFlushTimeoutMs = offsetFlushTimeoutMs;
  }

  public Integer getErrorsMaxRetries() {
    return errorsMaxRetries;
  }

  public void setErrorsMaxRetries(Integer errorsMaxRetries) {
    this.errorsMaxRetries = errorsMaxRetries;
  }

  public Integer getErrorsRetryDelayInitialMs() {
    return errorsRetryDelayInitialMs;
  }

  public void setErrorsRetryDelayInitialMs(Integer errorsRetryDelayInitialMs) {
    this.errorsRetryDelayInitialMs = errorsRetryDelayInitialMs;
  }

  public Integer getErrorsRetryDelayMaxMs() {
    return errorsRetryDelayMaxMs;
  }

  public void setErrorsRetryDelayMaxMs(Integer errorsRetryDelayMaxMs) {
    this.errorsRetryDelayMaxMs = errorsRetryDelayMaxMs;
  }

  public Map<String, Map<String, String>> getTransforms() {
    return transforms;
  }

  public void setTransforms(Map<String, Map<String, String>> transforms) {
    this.transforms = transforms;
  }

  public Map<String, Map<String, String>> getPredicates() {
    return predicates;
  }

  public void setPredicates(Map<String, Map<String, String>> predicates) {
    this.predicates = predicates;
  }

  @Override
  public String toString() {
    return StackGresUtil.toPrettyYaml(this);
  }

}
