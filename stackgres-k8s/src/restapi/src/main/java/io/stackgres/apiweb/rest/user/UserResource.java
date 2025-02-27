/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.rest.user;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleRef;
import io.fabric8.kubernetes.api.model.rbac.RoleRefBuilder;
import io.fabric8.kubernetes.api.model.rbac.Subject;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.quarkus.security.Authenticated;
import io.stackgres.apiweb.ValidationGroups;
import io.stackgres.apiweb.configuration.WebApiProperty;
import io.stackgres.apiweb.dto.user.UserDto;
import io.stackgres.apiweb.dto.user.UserRoleRef;
import io.stackgres.apiweb.exception.ErrorResponse;
import io.stackgres.apiweb.transformer.UserTransformer;
import io.stackgres.common.StackGresContext;
import io.stackgres.common.resource.ResourceFinder;
import io.stackgres.common.resource.ResourceScanner;
import io.stackgres.common.resource.ResourceWriter;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.groups.ConvertGroup;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("users")
@RequestScoped
@Authenticated
@Tag(name = "user")
@APIResponse(responseCode = "400", description = "Bad Request",
    content = {@Content(
        mediaType = "application/json",
        schema = @Schema(implementation = ErrorResponse.class))})
@APIResponse(responseCode = "401", description = "Unauthorized",
    content = {@Content(
        mediaType = "application/json",
        schema = @Schema(implementation = ErrorResponse.class))})
@APIResponse(responseCode = "403", description = "Forbidden",
    content = {@Content(
        mediaType = "application/json",
        schema = @Schema(implementation = ErrorResponse.class))})
@APIResponse(responseCode = "500", description = "Internal Server Error",
    content = {@Content(
        mediaType = "application/json",
        schema = @Schema(implementation = ErrorResponse.class))})
public class UserResource {

  private String namespace;

  private final ResourceScanner<Secret> scanner;

  private final ResourceFinder<Secret> finder;

  private final ResourceWriter<Secret> writer;

  private final ResourceScanner<RoleBinding> roleBindingScanner;

  private final ResourceScanner<ClusterRoleBinding> clusterRoleBindingScanner;

  private final ResourceWriter<RoleBinding> roleBindingWriter;

  private final ResourceWriter<ClusterRoleBinding> clusterRoleBindingWriter;

  private final UserTransformer transformer;

  @Inject
  public UserResource(
      ResourceScanner<Secret> scanner,
      ResourceFinder<Secret> finder,
      ResourceWriter<Secret> writer,
      ResourceScanner<RoleBinding> roleBindingScanner,
      ResourceScanner<ClusterRoleBinding> clusterRoleBindingScanner,
      ResourceWriter<RoleBinding> roleBindingWriter,
      ResourceWriter<ClusterRoleBinding> clusterRoleBindingWriter,
      UserTransformer transformer) {
    this.scanner = scanner;
    this.finder = finder;
    this.writer = writer;
    this.roleBindingScanner = roleBindingScanner;
    this.clusterRoleBindingScanner = clusterRoleBindingScanner;
    this.roleBindingWriter = roleBindingWriter;
    this.clusterRoleBindingWriter = clusterRoleBindingWriter;
    this.transformer = transformer;
  }

  @PostConstruct
  public void init() {
    this.namespace = WebApiProperty.RESTAPI_NAMESPACE.getString();
  }

  @APIResponse(responseCode = "200", description = "OK",
      content = {@Content(
          mediaType = "application/json",
          schema = @Schema(type = SchemaType.ARRAY, implementation = UserDto.class))})
  @Operation(summary = "List users", description = """
      List users.

      ### RBAC permissions required

      * secret list
      * rolebinding list
      * clusterrolebinding list
      """)
  @GET
  public List<UserDto> list() {
    var roleBindings = roleBindingScanner.getResourcesWithLabels(
        Map.of(StackGresContext.AUTH_KEY, StackGresContext.AUTH_USER_VALUE));
    var clusterRoleBindings = clusterRoleBindingScanner.getResourcesWithLabels(
        Map.of(StackGresContext.AUTH_KEY, StackGresContext.AUTH_USER_VALUE));
    return scanner
        .getResourcesInNamespaceWithLabels(
            namespace,
            Map.of(StackGresContext.AUTH_KEY, StackGresContext.AUTH_USER_VALUE))
        .stream()
        .map(user -> transformer.toDto(user, roleBindings, clusterRoleBindings))
        .toList();
  }

  @APIResponse(responseCode = "200", description = "OK",
      content = {@Content(
          mediaType = "application/json",
          schema = @Schema(implementation = UserDto.class))})
  @Operation(summary = "Create a user", description = """
      Create a user.

      ### RBAC permissions required

      * secret create
      * rolebinding list
      * rolebinding create
      * rolebinding update
      * clusterrolebinding list
      * clusterrolebinding create
      * clusterrolebinding update
      """)
  @POST
  public UserDto create(@Valid @ConvertGroup(to = ValidationGroups.Post.class) UserDto resource,
      @Nullable @QueryParam("dryRun") Boolean dryRun) {
    if (resource.getMetadata() != null) {
      resource.getMetadata().setNamespace(namespace);
    }
    var roleBindings = roleBindingScanner.getResourcesWithLabels(
        Map.of(StackGresContext.AUTH_KEY, StackGresContext.AUTH_USER_VALUE));
    var clusterRoleBindings = clusterRoleBindingScanner.getResourcesWithLabels(
        Map.of(StackGresContext.AUTH_KEY, StackGresContext.AUTH_USER_VALUE));
    Optional.ofNullable(resource.getRoles()).stream()
        .flatMap(List::stream)
        .forEach(userRoleRef -> setRoleBinding(
            resource, userRoleRef, roleBindings,
            Optional.ofNullable(dryRun).orElse(false)));
    Optional.ofNullable(resource.getClusterRoles()).stream()
        .flatMap(List::stream)
        .forEach(userRoleRef -> setClusterRoleBinding(
            resource, userRoleRef, clusterRoleBindings,
            Optional.ofNullable(dryRun).orElse(false)));
    return transformer.toDto(writer
        .create(
            transformer.toCustomResource(resource, null),
            Optional.ofNullable(dryRun).orElse(false)),
        roleBindings, clusterRoleBindings);
  }

  @APIResponse(responseCode = "200", description = "OK")
  @Operation(summary = "Delete a user", description = """
      Delete a user.

      ### RBAC permissions required

      * secret delete
      * rolebinding list
      * rolebinding update
      * rolebinding delete
      * clusterrolebinding list
      * clusterrolebinding update
      * clusterrolebinding delete
      """)
  @DELETE
  public void delete(@Valid UserDto resource, @Nullable @QueryParam("dryRun") Boolean dryRun) {
    if (resource.getMetadata() != null) {
      resource.getMetadata().setNamespace(namespace);
    }
    var roleBindings = roleBindingScanner.getResourcesWithLabels(
        Map.of(StackGresContext.AUTH_KEY, StackGresContext.AUTH_USER_VALUE));
    var clusterRoleBindings = clusterRoleBindingScanner.getResourcesWithLabels(
        Map.of(StackGresContext.AUTH_KEY, StackGresContext.AUTH_USER_VALUE));
    var foundResource = finder.findByNameAndNamespace(
        resource.getMetadata().getName(),
        resource.getMetadata().getNamespace())
        .map(found -> transformer.toDto(found, roleBindings, clusterRoleBindings))
        .orElse(null);
    writer.delete(transformer.toCustomResource(resource, null),
        Optional.ofNullable(dryRun).orElse(false));
    if (!Optional.ofNullable(dryRun).orElse(false) && foundResource != null) {
      Optional.ofNullable(foundResource.getRoles()).stream()
          .flatMap(List::stream)
          .forEach(userRoleRef -> unsetRoleBinding(
              foundResource, userRoleRef, roleBindings,
              Optional.ofNullable(dryRun).orElse(false)));
      Optional.ofNullable(foundResource.getClusterRoles()).stream()
          .flatMap(List::stream)
          .forEach(userRoleRef -> unsetClusterRoleBinding(
              foundResource, userRoleRef, clusterRoleBindings,
              Optional.ofNullable(dryRun).orElse(false)));
    }
  }

  @APIResponse(responseCode = "200", description = "OK",
      content = {@Content(
          mediaType = "application/json",
          schema = @Schema(implementation = UserDto.class))})
  @Operation(summary = "Update a user", description = """
      Update a user.

      ### RBAC permissions required

      * secret get
      * secret patch
      * rolebinding list
      * rolebinding create
      * rolebinding update
      * rolebinding delete
      * clusterrolebinding list
      * clusterrolebinding create
      * clusterrolebinding update
      * clusterrolebinding delete
      """)
  @PUT
  public UserDto update(@Valid UserDto resource, @Nullable @QueryParam("dryRun") Boolean dryRun) {
    if (resource.getMetadata() != null) {
      resource.getMetadata().setNamespace(namespace);
    }
    var roleBindings = roleBindingScanner.getResourcesWithLabels(
        Map.of(StackGresContext.AUTH_KEY, StackGresContext.AUTH_USER_VALUE));
    var clusterRoleBindings = clusterRoleBindingScanner.getResourcesWithLabels(
        Map.of(StackGresContext.AUTH_KEY, StackGresContext.AUTH_USER_VALUE));
    Secret transformedResource = transformer.toCustomResource(
        resource,
        finder.findByNameAndNamespace(
            resource.getMetadata().getName(), resource.getMetadata().getNamespace())
            .orElseThrow(NotFoundException::new));
    var foundResource = finder.findByNameAndNamespace(
        resource.getMetadata().getName(),
        resource.getMetadata().getNamespace())
        .map(found -> transformer.toDto(found, roleBindings, clusterRoleBindings))
        .orElse(null);
    if (foundResource != null) {
      Optional.ofNullable(resource.getRoles()).stream()
          .flatMap(List::stream)
          .forEach(userRoleRef -> setRoleBinding(
              resource, userRoleRef, roleBindings,
              Optional.ofNullable(dryRun).orElse(false)));
      Optional.ofNullable(resource.getClusterRoles()).stream()
          .flatMap(List::stream)
          .forEach(userRoleRef -> setClusterRoleBinding(
              resource, userRoleRef, clusterRoleBindings,
              Optional.ofNullable(dryRun).orElse(false)));
      Optional.ofNullable(foundResource.getRoles()).stream()
          .flatMap(List::stream)
          .filter(foundUserRoleRef -> Optional.ofNullable(resource.getRoles()).stream()
              .flatMap(List::stream)
              .noneMatch(userRoleRef -> userRoleRef.getName().equals(foundUserRoleRef.getName())
                  && userRoleRef.getNamespace().equals(foundUserRoleRef.getNamespace())))
          .forEach(removedUserRoleRef -> unsetRoleBinding(
              resource, removedUserRoleRef, roleBindings,
              Optional.ofNullable(dryRun).orElse(false)));
      Optional.ofNullable(foundResource.getClusterRoles()).stream()
          .flatMap(List::stream)
          .filter(foundUserRoleRef -> Optional.ofNullable(resource.getClusterRoles()).stream()
              .flatMap(List::stream)
              .noneMatch(userRoleRef -> userRoleRef.getName().equals(foundUserRoleRef.getName())))
          .forEach(removedUserRoleRef -> unsetClusterRoleBinding(
              resource, removedUserRoleRef, clusterRoleBindings,
              Optional.ofNullable(dryRun).orElse(false)));
    }
    if (Optional.ofNullable(dryRun).orElse(false)) {
      return transformer.toDto(
          writer.update(
              transformedResource,
              Optional.ofNullable(dryRun).orElse(false)),
          roleBindings, clusterRoleBindings);
    }
    return transformer.toDto(writer
        .update(transformedResource,
            currentResource -> updateSpec(currentResource, transformedResource)),
        roleBindings, clusterRoleBindings);
  }

  private void updateSpec(
      Secret resourceToUpdate, Secret resource) {
    resourceToUpdate.getMetadata().setLabels(resource.getMetadata().getLabels());
    resourceToUpdate.setData(resource.getData());
  }

  private void setRoleBinding(
      UserDto user, UserRoleRef userRoleRef, List<RoleBinding> roleBindings, boolean dryRun) {
    String namespace = userRoleRef.getNamespace();
    Subject userSubject = user.getSubject();
    if (roleBindings.stream()
        .anyMatch(roleBinding -> roleMatchUserRoleRef(
            userRoleRef, namespace, userSubject, roleBinding))) {
      return;
    }
    String name = getRoleName(user, userRoleRef.getName());
    var userRoleBinding = new RoleBindingBuilder()
        .withNewMetadata()
        .withNamespace(namespace)
        .withName(name)
        .withLabels(Map.of(StackGresContext.AUTH_KEY, StackGresContext.AUTH_USER_VALUE))
        .endMetadata()
        .withRoleRef(getRoleRef(userRoleRef))
        .withSubjects(List.of(userSubject))
        .build();
    if (roleBindings.stream()
        .anyMatch(roleBinding -> roleBinding.getMetadata().getName().equals(name))) {
      roleBindingWriter.update(userRoleBinding, dryRun);
    } else {
      try {
        roleBindingWriter.create(userRoleBinding, dryRun);
      } catch (KubernetesClientException ex) {
        if (ex.getCode() == 409) {
          roleBindingWriter.update(userRoleBinding, dryRun);
        } else {
          throw ex;
        }
      }
    }
    roleBindings.add(userRoleBinding);
  }

  private void unsetRoleBinding(
      UserDto user, UserRoleRef roleRef, List<RoleBinding> roleBindings, boolean dryRun) {
    String namespace = roleRef.getNamespace();
    Subject userSubject = user.getSubject();
    if (roleBindings.stream()
        .noneMatch(roleBinding -> roleMatchUserRoleRef(
            roleRef, namespace, userSubject, roleBinding))) {
      return;
    }
    roleBindings.stream()
        .toList()
        .stream()
        .filter(roleBinding -> roleMatchUserRoleRef(
            roleRef, namespace, userSubject, roleBinding))
        .forEach(roleBinding -> {
          if (roleBinding.getSubjects().size() == 1) {
            roleBindings.remove(roleBinding);
            roleBindingWriter.delete(roleBinding, dryRun);
          } else {
            roleBinding.setSubjects(new ArrayList<>(
                roleBinding.getSubjects().stream()
                    .filter(userSubject::equals)
                    .toList()));
            roleBindingWriter.update(roleBinding, dryRun);
          }
        });
  }

  private boolean roleMatchUserRoleRef(
      UserRoleRef userRoleRef,
      String namespace,
      Subject userSubject,
      RoleBinding roleBinding) {
    return roleBinding.getMetadata().getNamespace().equals(namespace)
        && roleBinding.getRoleRef().getKind().equals(HasMetadata.getKind(Role.class))
        && roleBinding.getRoleRef().getApiGroup().equals(HasMetadata.getGroup(Role.class))
        && roleBinding.getRoleRef().getName().equals(userRoleRef.getName())
        && Optional.ofNullable(roleBinding.getSubjects()).stream()
            .flatMap(List::stream)
            .anyMatch(userSubject::equals);
  }

  private RoleRef getRoleRef(UserRoleRef userRoleRef) {
    return new RoleRefBuilder()
        .withApiGroup(HasMetadata.getGroup(Role.class))
        .withKind(HasMetadata.getKind(Role.class))
        .withName(userRoleRef.getName())
        .build();
  }

  private void setClusterRoleBinding(
      UserDto user, UserRoleRef roleRef, List<ClusterRoleBinding> clusterRoleBindings, boolean dryRun) {
    Subject userSubject = user.getSubject();
    if (clusterRoleBindings.stream()
        .anyMatch(clusterRoleBinding -> clusterRoleMatchUserRoleRef(
            roleRef, userSubject, clusterRoleBinding))) {
      return;
    }
    String name = getRoleName(user, roleRef.getName());
    var userClusterRoleBinding = new ClusterRoleBindingBuilder()
        .withNewMetadata()
        .withName(name)
        .withLabels(Map.of(StackGresContext.AUTH_KEY, StackGresContext.AUTH_USER_VALUE))
        .endMetadata()
        .withRoleRef(getClusterRoleRef(roleRef))
        .withSubjects(List.of(userSubject))
        .build();
    if (clusterRoleBindings.stream()
        .anyMatch(clusterRoleBinding -> clusterRoleBinding.getMetadata().getName().equals(name))) {
      clusterRoleBindingWriter.update(userClusterRoleBinding, dryRun);
    } else {
      try {
        clusterRoleBindingWriter.create(userClusterRoleBinding, dryRun);
      } catch (KubernetesClientException ex) {
        if (ex.getCode() == 409) {
          clusterRoleBindingWriter.update(userClusterRoleBinding, dryRun);
        } else {
          throw ex;
        }
      }
    }
    clusterRoleBindings.add(userClusterRoleBinding);
  }

  private void unsetClusterRoleBinding(
      UserDto user, UserRoleRef roleRef, List<ClusterRoleBinding> clusterRoleBindings, boolean dryRun) {
    Subject userSubject = user.getSubject();
    if (clusterRoleBindings.stream()
        .noneMatch(clusterRoleBinding -> clusterRoleMatchUserRoleRef(
            roleRef, userSubject, clusterRoleBinding))) {
      return;
    }
    clusterRoleBindings.stream()
        .toList()
        .stream()
        .filter(clusterRoleBinding -> clusterRoleMatchUserRoleRef(
            roleRef, userSubject, clusterRoleBinding))
        .forEach(clusterRoleBinding -> {
          if (clusterRoleBinding.getSubjects().size() == 1) {
            clusterRoleBindings.remove(clusterRoleBinding);
            clusterRoleBindingWriter.delete(clusterRoleBinding, dryRun);
          } else {
            clusterRoleBinding.setSubjects(new ArrayList<>(
                clusterRoleBinding.getSubjects().stream()
                    .filter(userSubject::equals)
                    .toList()));
            clusterRoleBindingWriter.update(clusterRoleBinding, dryRun);
          }
        });
  }

  private boolean clusterRoleMatchUserRoleRef(
      UserRoleRef userRoleRef,
      Subject userSubject,
      ClusterRoleBinding clusterRoleBinding) {
    return clusterRoleBinding.getRoleRef().getKind().equals(HasMetadata.getKind(ClusterRole.class))
        && clusterRoleBinding.getRoleRef().getApiGroup().equals(HasMetadata.getGroup(ClusterRole.class))
        && clusterRoleBinding.getRoleRef().getName().equals(userRoleRef.getName())
        && Optional.ofNullable(clusterRoleBinding.getSubjects()).stream()
            .flatMap(List::stream)
            .anyMatch(userSubject::equals);
  }

  private RoleRef getClusterRoleRef(UserRoleRef userRoleRef) {
    return new RoleRefBuilder()
        .withApiGroup(HasMetadata.getGroup(ClusterRole.class))
        .withKind(HasMetadata.getKind(ClusterRole.class))
        .withName(userRoleRef.getName())
        .build();
  }

  public static String getRoleName(UserDto user, String roleName) {
    return "stackgres-" + user.getMetadata().getName() + "-" + roleName;
  }

}
