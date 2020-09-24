/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.rest;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

import javax.inject.Inject;

import com.google.common.collect.ImmutableList;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.stackgres.apiweb.dto.Metadata;
import io.stackgres.apiweb.dto.cluster.ClusterDto;
import io.stackgres.apiweb.dto.cluster.ClusterScriptEntry;
import io.stackgres.apiweb.dto.cluster.ClusterScriptFrom;
import io.stackgres.apiweb.dto.cluster.ConfigMapKeySelectorDto;
import io.stackgres.apiweb.dto.cluster.SecretKeySelectorDto;
import io.stackgres.common.KubernetesClientFactory;
import io.stackgres.common.resource.ResourceFinder;
import io.stackgres.testutil.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@QuarkusTest
@EnabledIfEnvironmentVariable(named = "QUARKUS_PROFILE", matches = "test")
class ClusterResourceIt implements AuthenticatedResourceTest {

  @Inject
  KubernetesClientFactory factory;

  @BeforeEach
  void setUp() {
    try (KubernetesClient client = factory.create()) {
      client.secrets().inNamespace("test").delete();
      client.configMaps().inNamespace("test").delete();
    }
  }

  private ClusterDto getClusterInlineScripts() {
    return JsonUtil.readFromJson("stackgres_cluster/inline_scripts.json", ClusterDto.class);
  }

  @Test
  void givenACreationWithInlineScripts_shouldNotFail() {

    ClusterDto cluster = getClusterInlineScripts();
    final Metadata metadata = cluster.getMetadata();
    metadata.setNamespace("test");

    given()
        .header(AUTHENTICATION_HEADER)
        .body(cluster)
        .contentType(ContentType.JSON)
        .accept(ContentType.JSON)
        .post("/stackgres/sgcluster")
        .then().statusCode(204);

  }

  @Test
  void givenACreationWithConfigMapsScripts_shouldNotFail() {

    ClusterDto cluster = getClusterInlineScripts();
    ClusterScriptEntry entry = getConfigMapScriptEntry();

    cluster.getSpec().getInitData().setScripts(Collections.singletonList(entry));
    final Metadata metadata = cluster.getMetadata();
    metadata.setNamespace("test");

    given()
        .header(AUTHENTICATION_HEADER)
        .body(cluster)
        .contentType(ContentType.JSON)
        .accept(ContentType.JSON)
        .post("/stackgres/sgcluster")
        .then().statusCode(204);

    try (KubernetesClient client = factory.create()){
      ConfigMap configMap = client.configMaps().inNamespace("test")
          .withName(entry.getScriptFrom().getConfigMapKeyRef().getName())
          .get();
      assertNotNull(configMap);

      String actualConfigScript = configMap.getData().get(entry.getScriptFrom().getConfigMapKeyRef().getKey());
      assertEquals(entry.getScriptFrom().getConfigMapScript(), actualConfigScript);

    }

  }

  @Test
  void givenACreationWithSecretScripts_shouldNotFail() {

    ClusterDto cluster = getClusterInlineScripts();
    ClusterScriptEntry entry = getSecretScriptEntry();

    cluster.getSpec().getInitData().setScripts(Collections.singletonList(entry));
    final Metadata metadata = cluster.getMetadata();
    metadata.setNamespace("test");

    given()
        .header(AUTHENTICATION_HEADER)
        .body(cluster)
        .contentType(ContentType.JSON)
        .accept(ContentType.JSON)
        .post("/stackgres/sgcluster")
        .then().statusCode(204);

    try (KubernetesClient client = factory.create()){
      final ClusterScriptFrom scriptFrom = entry.getScriptFrom();
      final SecretKeySelectorDto secretKeyRef = scriptFrom.getSecretKeyRef();
      Secret secret = client.secrets().inNamespace("test")
          .withName(secretKeyRef.getName())
          .get();
      assertNotNull(secret);

      byte[] actualScript = Base64.getDecoder().decode(secret.getData().get(secretKeyRef.getKey()));
      assertEquals(scriptFrom.getSecretScript(),
          new String(actualScript, StandardCharsets.UTF_8));
    }

  }

  @Test
  void givenACreationWithSecretAndConfigMapScripts_shouldNotFail() {

    ClusterDto cluster = getClusterInlineScripts();
    ClusterScriptEntry secretScriptEntry = getSecretScriptEntry();
    ClusterScriptEntry configMapScriptEntry = getConfigMapScriptEntry();

    cluster.getSpec().getInitData().setScripts(ImmutableList
        .of(secretScriptEntry, configMapScriptEntry));

    final Metadata metadata = cluster.getMetadata();
    metadata.setNamespace("test");

    given()
        .header(AUTHENTICATION_HEADER)
        .body(cluster)
        .contentType(ContentType.JSON)
        .accept(ContentType.JSON)
        .post("/stackgres/sgcluster")
        .then().statusCode(204);

    try (KubernetesClient client = factory.create()){
      final ClusterScriptFrom secretScriptFrom = secretScriptEntry.getScriptFrom();
      final SecretKeySelectorDto secretKeyRef = secretScriptFrom.getSecretKeyRef();
      Secret secret = client.secrets().inNamespace("test")
          .withName(secretKeyRef.getName())
          .get();
      assertNotNull(secret);

      byte[] actualScript = Base64.getDecoder().decode(secret.getData().get(secretKeyRef.getKey()));
      assertEquals(secretScriptFrom.getSecretScript(),
          new String(actualScript, StandardCharsets.UTF_8));

      final ClusterScriptFrom configMapScriptFrom = configMapScriptEntry.getScriptFrom();
      final ConfigMapKeySelectorDto configMapKeyRef = configMapScriptFrom.getConfigMapKeyRef();
      ConfigMap configMap = client.configMaps().inNamespace("test")
          .withName(configMapKeyRef.getName())
          .get();

      assertNotNull(configMap);

      assertEquals(configMapScriptFrom.getConfigMapScript(),
          configMap.getData().get(configMapKeyRef.getKey()));

    }

  }

  private ClusterScriptEntry getSecretScriptEntry() {
    ClusterScriptEntry entry = new ClusterScriptEntry();
    entry.setName("init");
    final ClusterScriptFrom scriptFrom = new ClusterScriptFrom();
    scriptFrom.setSecretScript("CREATE DATABASE test");
    final SecretKeySelectorDto secretMapKeyRef = new SecretKeySelectorDto();
    scriptFrom.setSecretKeyRef(secretMapKeyRef);
    secretMapKeyRef.setKey("script");
    secretMapKeyRef.setName("initScript");
    entry.setScriptFrom(scriptFrom);
    return entry;
  }

  private ClusterScriptEntry getConfigMapScriptEntry() {
    ClusterScriptEntry entry = new ClusterScriptEntry();
    entry.setName("init");
    final ClusterScriptFrom scriptFrom = new ClusterScriptFrom();
    scriptFrom.setConfigMapScript("CREATE DATABASE test");
    final ConfigMapKeySelectorDto configMapKeyRef = new ConfigMapKeySelectorDto();
    scriptFrom.setConfigMapKeyRef(configMapKeyRef);
    configMapKeyRef.setKey("script");
    configMapKeyRef.setName("initScript");
    entry.setScriptFrom(scriptFrom);
    return entry;
  }

}