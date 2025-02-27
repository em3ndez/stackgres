/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.rest;

import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.client.CustomResource;
import io.quarkus.security.Authenticated;
import io.stackgres.apiweb.dto.ResourceDto;
import io.stackgres.apiweb.transformer.ResourceTransformer;
import io.stackgres.common.resource.CustomResourceFinder;
import io.stackgres.common.resource.CustomResourceScanner;
import io.stackgres.common.resource.CustomResourceScheduler;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.QueryParam;
import org.jetbrains.annotations.NotNull;
import org.jooq.lambda.Seq;

@Authenticated
public abstract class AbstractCustomResourceService
    <T extends ResourceDto, R extends CustomResource<?, ?>>
    implements ResourceRestService<T> {

  @Inject
  CustomResourceScanner<R> scanner;

  @Inject
  public CustomResourceFinder<R> finder;

  @Inject
  CustomResourceScheduler<R> scheduler;

  @Inject
  public ResourceTransformer<T, R> transformer;

  /**
   * Looks for all resources of type {@code <R>} that are installed in the kubernetes cluster.
   *
   * @return a list with the installed resources
   * @throws RuntimeException if no custom resource of type {@code <R>} is defined
   */
  @GET
  @Override
  public List<T> list() {
    return Seq.seq(scanner.getResources())
        .map(transformer::toDto)
        .toList();
  }

  /**
   * Creates a resource of type {@code <R>}.
   *
   * @param resource the resource to create
   */
  @POST
  @Override
  public T create(@NotNull T resource, @Nullable @QueryParam("dryRun") Boolean dryRun) {
    return transformer.toDto(
        scheduler.create(transformer.toCustomResource(resource, null),
        Optional.ofNullable(dryRun).orElse(false)));
  }

  /**
   * Deletes a custom resource of type {@code <R>}.
   *
   * @param resource the resource to delete
   */
  @DELETE
  @Override
  public void delete(@NotNull T resource, @Nullable @QueryParam("dryRun") Boolean dryRun) {
    scheduler.delete(transformer.toCustomResource(resource, null),
        Optional.ofNullable(dryRun).orElse(false));
  }

  /**
   * Updates a custom resource of type {@code <R>}.
   *
   * @param resource the resource to delete
   */
  @PUT
  @Override
  public T update(@NotNull T resource, @Nullable @QueryParam("dryRun") Boolean dryRun) {
    R transformedResource = transformer.toCustomResource(
        resource,
        finder.findByNameAndNamespace(
            resource.getMetadata().getName(), resource.getMetadata().getNamespace())
            .orElseThrow(NotFoundException::new));
    if (Optional.ofNullable(dryRun).orElse(false)) {
      return transformer.toDto(scheduler.update(
          transformedResource,
          Optional.ofNullable(dryRun).orElse(false)));
    }
    return transformer.toDto(scheduler.update(transformedResource,
        currentResource -> updateSpec(currentResource, transformedResource)));
  }

  protected abstract void updateSpec(R resourceToUpdate, R resource);

}
