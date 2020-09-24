/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.testutil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jooq.lambda.Unchecked;

public class CrdUtils {

  public static File getCrdsFolder() {
    String projectPath = new File(new File("src").getAbsolutePath())
        .getParentFile().getParentFile().getParentFile().getAbsolutePath();

    return new File(projectPath + "/install/helm/stackgres-operator/crds");
  }

  public static void installCrds(KubernetesClient client) throws IOException {
    YAMLMapper yamlMapper = new YAMLMapper();
    Files.list(Paths.get("../../install/helm/stackgres-operator/crds"))
        .forEach(Unchecked.consumer(path -> client.customResourceDefinitions()
            .create(yamlMapper.readValue(path.toFile(), CustomResourceDefinition.class))));
  }
}
