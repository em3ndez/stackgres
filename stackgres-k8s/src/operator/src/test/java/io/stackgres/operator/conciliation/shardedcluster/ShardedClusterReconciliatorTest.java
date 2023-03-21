/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.shardedcluster;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.stackgres.common.crd.Condition;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.event.EventEmitter;
import io.stackgres.common.fixture.Fixtures;
import io.stackgres.common.resource.CustomResourceScanner;
import io.stackgres.common.resource.CustomResourceScheduler;
import io.stackgres.operator.conciliation.ComparisonDelegator;
import io.stackgres.operator.conciliation.Conciliator;
import io.stackgres.operator.conciliation.HandlerDelegator;
import io.stackgres.operator.conciliation.ReconciliationResult;
import io.stackgres.operator.conciliation.StatusManager;
import io.stackgres.operator.conciliation.factory.cluster.KubernetessMockResourceGenerationUtil;
import org.hamcrest.MatcherAssert;
import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.Returns;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShardedClusterReconciliatorTest {

  private final StackGresShardedCluster cluster = Fixtures.shardedCluster().loadDefault().get();
  @Mock
  CustomResourceScanner<StackGresShardedCluster> scanner;
  @Mock
  Conciliator<StackGresShardedCluster> conciliator;
  @Mock
  HandlerDelegator<StackGresShardedCluster> handlerDelegator;
  @Mock
  StatusManager<StackGresShardedCluster, Condition> statusManager;
  @Mock
  EventEmitter<StackGresShardedCluster> eventController;
  @Mock
  CustomResourceScheduler<StackGresShardedCluster> clusterScheduler;
  @Mock
  ComparisonDelegator<StackGresShardedCluster> resourceComparator;

  private ShardedClusterReconciliator reconciliator;

  @BeforeEach
  void setUp() {
    ShardedClusterReconciliator.Parameters parameters =
        new ShardedClusterReconciliator.Parameters();
    parameters.scanner = scanner;
    parameters.conciliator = conciliator;
    parameters.handlerDelegator = handlerDelegator;
    parameters.eventController = eventController;
    parameters.statusManager = statusManager;
    parameters.clusterScheduler = clusterScheduler;
    parameters.resourceComparator = resourceComparator;
    reconciliator = new ShardedClusterReconciliator(parameters);
  }

  @Test
  void allCreations_shouldBePerformed() {
    when(scanner.getResources()).thenReturn(Collections.singletonList(cluster));

    final List<HasMetadata> creations = KubernetessMockResourceGenerationUtil
        .buildResources("test", "test");

    creations.forEach(resource -> when(handlerDelegator.create(cluster, resource))
        .thenReturn(resource));

    when(conciliator.evalReconciliationState(cluster))
        .thenReturn(new ReconciliationResult(
            creations,
            Collections.emptyList(),
            Collections.emptyList()));

    reconciliator.reconciliationCycle();

    verify(scanner).getResources();
    verify(conciliator).evalReconciliationState(cluster);
    creations.forEach(resource -> verify(handlerDelegator).create(cluster, resource));
  }

  @Test
  void allPatches_shouldBePerformed() {
    when(scanner.getResources()).thenReturn(Collections.singletonList(cluster));

    final List<Tuple2<HasMetadata, HasMetadata>> patches = KubernetessMockResourceGenerationUtil
        .buildResources("test", "test")
        .stream().map(r -> Tuple.tuple(r, r))
        .collect(Collectors.toUnmodifiableList());

    patches.forEach(resource -> when(handlerDelegator.patch(cluster, resource.v1, resource.v2))
        .thenReturn(resource.v1));

    when(conciliator.evalReconciliationState(cluster))
        .thenReturn(new ReconciliationResult(
            Collections.emptyList(),
            patches,
            Collections.emptyList()));

    reconciliator.reconciliationCycle();

    verify(scanner).getResources();
    verify(conciliator).evalReconciliationState(cluster);
    patches.forEach(resource -> verify(handlerDelegator).patch(cluster, resource.v1, resource.v2));
  }

  @Test
  void allDeletions_shouldBePerformed() {
    when(scanner.getResources()).thenReturn(Collections.singletonList(cluster));

    final List<HasMetadata> deletions = KubernetessMockResourceGenerationUtil
        .buildResources("test", "test");

    deletions.forEach(resource -> doNothing().when(handlerDelegator).delete(cluster, resource));

    when(conciliator.evalReconciliationState(cluster))
        .thenReturn(new ReconciliationResult(
            Collections.emptyList(),
            Collections.emptyList(),
            deletions));

    reconciliator.reconciliationCycle();

    verify(scanner).getResources();
    verify(conciliator).evalReconciliationState(cluster);
    deletions.forEach(resource -> verify(handlerDelegator).delete(cluster, resource));
  }

  @Test
  void reconciliator_shouldPreventTheConcurrentExecution() throws InterruptedException {

    long delay = 100;
    int concurrentExecutions = new Random().nextInt(2) + 2;

    doAnswer(new AnswersWithDelay(delay, new Returns(Collections.singletonList(cluster))))
        .when(scanner).getResources();

    when(conciliator.evalReconciliationState(cluster))
        .thenReturn(new ReconciliationResult(
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList()));

    var pool = ForkJoinPool.commonPool();
    long start = System.currentTimeMillis();
    for (int i = 0; i < concurrentExecutions; i++) {
      pool.execute(() -> reconciliator.reconciliationCycle());
    }

    pool.awaitTermination(delay * concurrentExecutions, TimeUnit.SECONDS);
    long end = System.currentTimeMillis();

    MatcherAssert
        .assertThat("Is being executed concurrently",
            end - start,
            greaterThanOrEqualTo(delay * concurrentExecutions));

    verify(scanner, times(concurrentExecutions)).getResources();
    verify(conciliator, times(concurrentExecutions)).evalReconciliationState(cluster);

  }
}
