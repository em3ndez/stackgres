/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.stream.jobs.lock;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.quarkus.test.Mock;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.resource.CustomResourceScheduler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jetbrains.annotations.NotNull;

@Mock
@ApplicationScoped
public class FakeClusterScheduler implements CustomResourceScheduler<StackGresCluster> {

  private final MockKubeDb kubeDb;

  @Inject
  public FakeClusterScheduler(MockKubeDb kubeDb) {
    this.kubeDb = kubeDb;
  }

  @Override
  public StackGresCluster create(@NotNull StackGresCluster resource, boolean dryRun) {
    return kubeDb.addOrReplaceCluster(resource);
  }

  @Override
  public void delete(@NotNull StackGresCluster resource, boolean dryRun) {
    kubeDb.delete(resource);
  }

  @Override
  public StackGresCluster update(@NotNull StackGresCluster resource, boolean dryRun) {
    return kubeDb.addOrReplaceCluster(resource);
  }

  @Override
  public StackGresCluster update(@NotNull StackGresCluster resource,
      @NotNull Consumer<StackGresCluster> setter) {
    final ObjectMeta metadata = resource.getMetadata();
    var cluster = kubeDb.getCluster(metadata.getName(), metadata.getNamespace());
    setter.accept(cluster);
    return kubeDb.addOrReplaceCluster(cluster);
  }

  @Override
  public <S> StackGresCluster updateStatus(@NotNull StackGresCluster resource,
      @NotNull Consumer<StackGresCluster> setter) {
    final ObjectMeta metadata = resource.getMetadata();
    var cluster = kubeDb.getCluster(metadata.getName(), metadata.getNamespace());
    setter.accept(cluster);
    return kubeDb.addOrReplaceCluster(cluster);
  }

}
