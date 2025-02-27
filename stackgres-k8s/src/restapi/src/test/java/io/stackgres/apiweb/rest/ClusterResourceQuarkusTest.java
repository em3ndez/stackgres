/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.KubernetesTestServer;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import io.restassured.http.ContentType;
import io.stackgres.apiweb.dto.Metadata;
import io.stackgres.apiweb.dto.backup.BaseBackupPerformance;
import io.stackgres.apiweb.dto.cluster.ClusterBackupConfiguration;
import io.stackgres.apiweb.dto.cluster.ClusterConfigurations;
import io.stackgres.apiweb.dto.cluster.ClusterDto;
import io.stackgres.apiweb.dto.cluster.ClusterSpec;
import io.stackgres.apiweb.dto.fixture.DtoFixtures;
import io.stackgres.apiweb.dto.script.ScriptEntry;
import io.stackgres.apiweb.dto.script.ScriptFrom;
import io.stackgres.apiweb.dto.script.ScriptSpec;
import io.stackgres.common.PatroniUtil;
import io.stackgres.common.StackGresKubernetesMockServerSetup;
import io.stackgres.common.crd.ConfigMapKeySelector;
import io.stackgres.common.crd.SecretKeySelector;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgcluster.StackGresClusterConfigurations;
import io.stackgres.common.crd.sgcluster.StackGresClusterList;
import io.stackgres.common.fixture.Fixtures;
import io.stackgres.testutil.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@WithKubernetesTestServer(https = true, setup = StackGresKubernetesMockServerSetup.class)
class ClusterResourceQuarkusTest implements AuthenticatedResourceTest {

  @KubernetesTestServer
  KubernetesServer mockServer;

  private final StackGresCluster cluster = getCluster();

  private final ClusterDto clusterDto = getClusterInlineScripts();

  @BeforeEach
  void setUp() {
    mockServer.getClient().secrets().inNamespace("test").delete();
    mockServer.getClient().configMaps().inNamespace("test").delete();

    clusterDto.getMetadata().setNamespace("test");
    cluster.getMetadata().setNamespace("test");
    cluster.getMetadata().setName(StringUtils.getRandomResourceName());
    cluster.getSpec().setConfigurations(new StackGresClusterConfigurations());
    mockServer.getClient().resources(
        StackGresCluster.class,
        StackGresClusterList.class)
        .resource(cluster)
        .create();

    Service primary = new ServiceBuilder()
        .withNewMetadata()
        .withName(cluster.getMetadata().getName())
        .withNamespace(cluster.getMetadata().getNamespace())
        .endMetadata()
        .withNewSpec()
        .withType(cluster.getSpec().getPostgresServices().getPrimary().getType() != null
            ? cluster.getSpec().getPostgresServices().getPrimary().getType()
            : null)
        .withClusterIP("10.10.100.8")
        .endSpec()
        .build();
    Service replicas = new ServiceBuilder()
        .withNewMetadata()
        .withName(PatroniUtil.readOnlyName(cluster.getMetadata().getName()))
        .withNamespace(cluster.getMetadata().getNamespace())
        .endMetadata()
        .withNewSpec()
        .withType(cluster.getSpec().getPostgresServices().getReplicas().getType() != null
            ? cluster.getSpec().getPostgresServices().getReplicas().getType()
            : null)
        .withClusterIP("10.10.100.30")
        .endSpec()
        .build();
    mockServer.getClient().services()
        .inNamespace(cluster.getMetadata().getNamespace())
        .resource(primary)
        .create();
    mockServer.getClient().services()
        .inNamespace(cluster.getMetadata().getNamespace())
        .resource(replicas)
        .create();
  }

  @AfterEach
  void tearDown() {
    mockServer.getClient().secrets().inNamespace("test").delete();
    mockServer.getClient().configMaps().inNamespace("test").delete();
    mockServer.getClient().resources(
        StackGresCluster.class,
        StackGresClusterList.class)
        .inNamespace(cluster.getMetadata().getNamespace())
        .withName(cluster.getMetadata().getName())
        .delete();
    mockServer.getClient().resources(
        StackGresCluster.class,
        StackGresClusterList.class)
        .inNamespace(clusterDto.getMetadata().getNamespace())
        .withName(clusterDto.getMetadata().getName())
        .delete();
  }

  private ClusterDto getClusterInlineScripts() {
    return DtoFixtures.cluster().loadInlineScripts().get();
  }

  private StackGresCluster getCluster() {
    return Fixtures.cluster().loadDefault().get();
  }

  @Test
  void givenACreationWithInlineScripts_shouldNotFail() {
    given()
        .header(AUTHENTICATION_HEADER)
        .body(clusterDto)
        .contentType(ContentType.JSON)
        .accept(ContentType.JSON)
        .post("/stackgres/sgclusters")
        .then()
        .statusCode(200);
  }

  @Test
  void getCluster_shouldNotFail() {
    given()
        .header(AUTHENTICATION_HEADER)
        .pathParam("namespace", cluster.getMetadata().getNamespace())
        .pathParam("name", cluster.getMetadata().getName())
        .contentType(ContentType.JSON)
        .accept(ContentType.JSON)
        .get("/stackgres/namespaces/{namespace}/sgclusters/{name}")
        .then()
        .body("metadata.namespace", equalTo("test"),
            "metadata.name", equalTo(cluster.getMetadata().getName()),
            "spec.instances", equalTo(1),
            "spec.postgres.version", equalTo("13.9"),
            "spec.sgInstanceProfile", equalTo("size-s"),
            "info.superuserSecretName", equalTo(cluster.getMetadata().getName()),
            "info.superuserPasswordKey", equalTo("superuser-password"),
            "info.primaryDns",
            equalTo(cluster.getMetadata().getName()
                + ".test"),
            "info.replicasDns",
            equalTo(PatroniUtil.readOnlyName(cluster.getMetadata().getName())
                + ".test"))
        .statusCode(200);
  }

  @Test
  void getListCluster_shouldNotFail() {
    given()
        .header(AUTHENTICATION_HEADER)
        .contentType(ContentType.JSON)
        .accept(ContentType.JSON)
        .get("/stackgres/sgclusters")
        .then()
        .body("[0].metadata.namespace", equalTo("test"),
            "[0].metadata.name", equalTo(cluster.getMetadata().getName()),
            "[0].spec.instances", equalTo(1),
            "[0].spec.postgres.version", equalTo("13.9"),
            "[0].spec.sgInstanceProfile", equalTo("size-s"),
            "[0].info.superuserSecretName", equalTo(cluster.getMetadata().getName()),
            "[0].info.superuserPasswordKey", equalTo("superuser-password"),
            "[0].info.primaryDns",
            equalTo(cluster.getMetadata().getName()
                + ".test"),
            "[0].info.replicasDns",
            equalTo(PatroniUtil.readOnlyName(cluster.getMetadata().getName())
                + ".test"))
        .statusCode(200);
  }

  @Test
  void givenACreationWithConfigMapsScripts_shouldNotFail() {
    ScriptSpec scriptSpec = getConfigMapScriptSpec();
    clusterDto.getSpec().getManagedSql().getScripts().get(0).setScriptSpec(scriptSpec);
    final Metadata metadata = clusterDto.getMetadata();
    metadata.setNamespace("test");

    given()
        .header(AUTHENTICATION_HEADER)
        .body(clusterDto)
        .contentType(ContentType.JSON)
        .accept(ContentType.JSON)
        .post("/stackgres/sgclusters")
        .then().statusCode(200);

    ConfigMap configMap = mockServer.getClient().configMaps().inNamespace("test")
        .withName(scriptSpec.getScripts().get(0).getScriptFrom().getConfigMapKeyRef().getName())
        .get();
    assertNotNull(configMap);

    String actualConfigScript =
        configMap.getData().get(scriptSpec.getScripts().get(0).getScriptFrom()
            .getConfigMapKeyRef().getKey());
    assertEquals(scriptSpec.getScripts().get(0).getScriptFrom().getConfigMapScript(),
        actualConfigScript);
  }

  @Test
  void givenACreationWithSecretScripts_shouldNotFail() {
    ScriptSpec scriptSpec = getSecretScriptSpec();
    clusterDto.getSpec().getManagedSql().getScripts().get(0).setScriptSpec(scriptSpec);
    final Metadata metadata = clusterDto.getMetadata();
    metadata.setNamespace("test");

    given()
        .header(AUTHENTICATION_HEADER)
        .body(clusterDto)
        .contentType(ContentType.JSON)
        .accept(ContentType.JSON)
        .post("/stackgres/sgclusters")
        .then().statusCode(200);

    final ScriptFrom scriptFrom = scriptSpec.getScripts().get(0).getScriptFrom();
    final SecretKeySelector secretKeyRef = scriptFrom.getSecretKeyRef();
    Secret secret = mockServer.getClient().secrets().inNamespace("test")
        .withName(secretKeyRef.getName())
        .get();
    assertNotNull(secret);

    byte[] actualScript = Base64.getDecoder().decode(secret.getData().get(secretKeyRef.getKey()));
    assertEquals(scriptFrom.getSecretScript(),
        new String(actualScript, StandardCharsets.UTF_8));
  }

  @Test
  void givenACreationWithSecretAndConfigMapScripts_shouldNotFail() {
    ScriptEntry secretScriptEntry = getSecretScriptSpec().getScripts().get(0);
    ScriptEntry configMapScriptEntry = getConfigMapScriptSpec().getScripts().get(0);
    ScriptSpec scriptSpec = getSecretScriptSpec();
    scriptSpec.setScripts(List.of(secretScriptEntry, configMapScriptEntry));
    clusterDto.getSpec().getManagedSql().getScripts().get(0).setScriptSpec(scriptSpec);

    given()
        .header(AUTHENTICATION_HEADER)
        .body(clusterDto)
        .contentType(ContentType.JSON)
        .accept(ContentType.JSON)
        .post("/stackgres/sgclusters")
        .then().statusCode(200);

    final ScriptFrom secretScriptFrom = secretScriptEntry.getScriptFrom();
    final SecretKeySelector secretKeyRef = secretScriptFrom.getSecretKeyRef();
    Secret secret = mockServer.getClient().secrets().inNamespace("test")
        .withName(secretKeyRef.getName())
        .get();
    assertNotNull(secret);

    byte[] actualScript = Base64.getDecoder().decode(secret.getData().get(secretKeyRef.getKey()));
    assertEquals(secretScriptFrom.getSecretScript(),
        new String(actualScript, StandardCharsets.UTF_8));

    final ScriptFrom configMapScriptFrom = configMapScriptEntry.getScriptFrom();
    final ConfigMapKeySelector configMapKeyRef = configMapScriptFrom.getConfigMapKeyRef();
    ConfigMap configMap = mockServer.getClient().configMaps().inNamespace("test")
        .withName(configMapKeyRef.getName())
        .get();

    assertNotNull(configMap);

    assertEquals(configMapScriptFrom.getConfigMapScript(),
        configMap.getData().get(configMapKeyRef.getKey()));
  }

  private ScriptSpec getSecretScriptSpec() {
    ScriptSpec scriptSpec = new ScriptSpec();
    ScriptEntry entry = new ScriptEntry();
    scriptSpec.setScripts(List.of(entry));
    entry.setName("init");
    final ScriptFrom scriptFrom = new ScriptFrom();
    scriptFrom.setSecretScript("CREATE DATABASE test");
    final SecretKeySelector secretMapKeyRef = new SecretKeySelector();
    scriptFrom.setSecretKeyRef(secretMapKeyRef);
    secretMapKeyRef.setKey("script");
    secretMapKeyRef.setName("initScript");
    entry.setScriptFrom(scriptFrom);
    return scriptSpec;
  }

  private ScriptSpec getConfigMapScriptSpec() {
    ScriptSpec scriptSpec = new ScriptSpec();
    ScriptEntry entry = new ScriptEntry();
    scriptSpec.setScripts(List.of(entry));
    entry.setName("init");
    final ScriptFrom scriptFrom = new ScriptFrom();
    scriptFrom.setConfigMapScript("CREATE DATABASE test");
    final ConfigMapKeySelector configMapKeyRef = new ConfigMapKeySelector();
    scriptFrom.setConfigMapKeyRef(configMapKeyRef);
    configMapKeyRef.setKey("script");
    configMapKeyRef.setName("initScript");
    entry.setScriptFrom(scriptFrom);
    return scriptSpec;
  }

  @Test
  void givenACreationWithBackups_shouldNotFail() {
    clusterDto.getMetadata().setName(StringUtils.getRandomResourceName());
    ClusterSpec spec = clusterDto.getSpec();
    spec.setInitialData(null);
    spec.setConfigurations(new ClusterConfigurations());
    spec.getConfigurations().setBackups(new ArrayList<>());

    ClusterBackupConfiguration clusterBackupsConfiguration = new ClusterBackupConfiguration();
    clusterBackupsConfiguration.setCompression("brotli");
    clusterBackupsConfiguration.setCronSchedule("100 8 10 10 10");
    clusterBackupsConfiguration.setRetention(10);
    clusterBackupsConfiguration.setSgObjectStorage("backupconf");
    clusterBackupsConfiguration.setPerformance(new BaseBackupPerformance());
    clusterBackupsConfiguration.getPerformance().setMaxDiskBandwidth(10L);
    clusterBackupsConfiguration.getPerformance().setMaxNetworkBandwidth(10L);
    clusterBackupsConfiguration.getPerformance().setUploadDiskConcurrency(10);
    clusterBackupsConfiguration.getPerformance().setUploadConcurrency(10);

    spec.getConfigurations().getBackups().add(clusterBackupsConfiguration);

    given()
        .header(AUTHENTICATION_HEADER)
        .body(clusterDto)
        .contentType(ContentType.JSON)
        .accept(ContentType.JSON)
        .post("/stackgres/sgclusters")
        .then().statusCode(200);

    given()
        .header(AUTHENTICATION_HEADER)
        .pathParam("namespace", clusterDto.getMetadata().getNamespace())
        .pathParam("name", clusterDto.getMetadata().getName())
        .contentType(ContentType.JSON)
        .accept(ContentType.JSON)
        .get("/stackgres/namespaces/{namespace}/sgclusters/{name}")
        .then()
        .body("metadata.namespace", equalTo("test"),
            "metadata.name", equalTo(clusterDto.getMetadata().getName()),
            "spec.instances", equalTo(1),
            "spec.configurations.backups[0].compression", equalTo("brotli"),
            "spec.configurations.backups[0].cronSchedule", equalTo("100 8 10 10 10"),
            "spec.configurations.backups[0].retention", equalTo(10),
            "spec.configurations.backups[0].sgObjectStorage", equalTo("backupconf"),
            "spec.configurations.backups[0].performance.maxNetworkBandwidth", equalTo(10),
            "spec.configurations.backups[0].performance.maxDiskBandwidth", equalTo(10),
            "spec.configurations.backups[0].performance.uploadDiskConcurrency", equalTo(10),
            "spec.configurations.backups[0].performance.uploadConcurrency", equalTo(10))
        .statusCode(200);
  }

}
