/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.app;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.stackgres.common.OperatorProperty;
import io.stackgres.common.app.ReconciliationClock;
import io.stackgres.operator.configuration.OperatorPropertyContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class StackGresOperatorApp {

  private static final Logger LOGGER = LoggerFactory.getLogger(StackGresOperatorApp.class);

  private final OperatorPropertyContext operatorPropertyContext;
  private final ExecutorService executorService;
  private final OperatorWatchersHandler operatorWatchersHandler;
  private final ReconciliationClock reconciliationClock;
  private final OperatorBootstrap operatorBootstrap;
  private final OperatorLockHolder operatorLockHolder;

  @Inject
  public StackGresOperatorApp(
      OperatorPropertyContext operatorPropertyContext,
      OperatorWatchersHandler operatorWatchersHandler,
      ReconciliationClock reconciliationClock,
      OperatorBootstrap operatorBootstrap,
      OperatorLockHolder operatorLockHolder) {
    this.operatorPropertyContext = operatorPropertyContext;
    this.executorService = Executors.newSingleThreadExecutor(
        r -> new Thread(r, "OperatorStartup"));
    this.operatorWatchersHandler = operatorWatchersHandler;
    this.reconciliationClock = reconciliationClock;
    this.operatorBootstrap = operatorBootstrap;
    this.operatorLockHolder = operatorLockHolder;
  }

  void onStart(@Observes StartupEvent ev) {
    if (!operatorPropertyContext.getBoolean(OperatorProperty.DISABLE_BOOTSTRAP)) {
      this.operatorBootstrap.syncBootstrap();
      this.executorService.execute(this::asyncBootstrap);
    }
    if (!operatorPropertyContext.getBoolean(OperatorProperty.DISABLE_RECONCILIATION)) {
      this.executorService.execute(this::startReconciliation);
    }
  }

  void onStop(@Observes ShutdownEvent ev) {
    operatorLockHolder.stop();
    if (!operatorPropertyContext.getBoolean(OperatorProperty.DISABLE_RECONCILIATION)) {
      stopReconciliation();
    }
  }

  private void asyncBootstrap() {
    LOGGER.info("The operator is starting...");
    try {
      operatorBootstrap.bootstrap();
      if (operatorPropertyContext.getBoolean(OperatorProperty.STOP_AFTER_BOOTSTRAP)) {
        Quarkus.asyncExit(0);
      }
    } catch (Exception ex) {
      Quarkus.asyncExit(1);
      throw ex;
    }
  }

  private void startReconciliation() {
    LOGGER.info("The operator is starting...");
    try {
      operatorWatchersHandler.startWatchers();
      reconciliationClock.start();
    } catch (Exception ex) {
      Quarkus.asyncExit(1);
      throw ex;
    }
  }

  private void stopReconciliation() {
    LOGGER.info("The operator is stopping...");
    this.executorService.shutdown();
    try {
      this.executorService.awaitTermination(3, TimeUnit.SECONDS);
    } catch (Exception ex) {
      LOGGER.warn("Can not stop bostrap executor", ex);
    }
    reconciliationClock.stop();
    operatorWatchersHandler.stopWatchers();
  }

}
