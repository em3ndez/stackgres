/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation.shardedcluster;

import static io.stackgres.common.StackGresUtil.getPostgresFlavorComponent;
import static io.stackgres.operatorframework.resource.ResourceUtil.getServiceAccountFromUsername;
import static io.stackgres.operatorframework.resource.ResourceUtil.isServiceAccountUsername;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.stackgres.common.ErrorType;
import io.stackgres.common.StackGresComponent;
import io.stackgres.common.StackGresUtil;
import io.stackgres.common.StackGresVersion;
import io.stackgres.common.crd.sgcluster.StackGresClusterConfigurations;
import io.stackgres.common.crd.sgcluster.StackGresClusterPostgres;
import io.stackgres.common.crd.sgcluster.StackGresClusterSpec;
import io.stackgres.common.crd.sgpgconfig.StackGresPostgresConfig;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterCoordinator;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterShard;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterShardConfigurations;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterShards;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterSpec;
import io.stackgres.common.resource.CustomResourceFinder;
import io.stackgres.operator.common.StackGresShardedClusterReview;
import io.stackgres.operator.validation.AbstractReferenceValidator;
import io.stackgres.operator.validation.ValidationType;
import io.stackgres.operator.validation.ValidationUtil;
import io.stackgres.operatorframework.admissionwebhook.validating.ValidationFailed;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;

@Singleton
@ValidationType(ErrorType.INVALID_CR_REFERENCE)
public class PostgresConfigValidator implements ShardedClusterValidator {

  private static final String PG_14_CREATE_CONCURRENT_INDEX_BUG =
      "Please, use PostgreSQL 14.4 since it fixes an issue"
          + " with CREATE INDEX CONCURRENTLY and REINDEX CONCURRENTLY that"
          + " could cause silent data corruption of indexes. For more info"
          + " see https://www.postgresql.org/about/news/postgresql-144-released-2470/.";
  public static final Map<String, String> BUGGY_PG_VERSIONS = Map.ofEntries(
      Map.entry("14.0", PG_14_CREATE_CONCURRENT_INDEX_BUG),
      Map.entry("14.1", PG_14_CREATE_CONCURRENT_INDEX_BUG),
      Map.entry("14.2", PG_14_CREATE_CONCURRENT_INDEX_BUG),
      Map.entry("14.3", PG_14_CREATE_CONCURRENT_INDEX_BUG)
      );

  private final CustomResourceFinder<StackGresPostgresConfig> configFinder;

  private final Map<StackGresComponent, Map<StackGresVersion, List<String>>>
      supportedPostgresVersions;

  private final String errorPostgresMismatchUri;
  private final String errorForbiddenUpdateUri;

  @Inject
  public PostgresConfigValidator(
      CustomResourceFinder<StackGresPostgresConfig> configFinder) {
    this(configFinder, ValidationUtil.SUPPORTED_POSTGRES_VERSIONS);
  }

  public PostgresConfigValidator(
      CustomResourceFinder<StackGresPostgresConfig> configFinder,
      Map<StackGresComponent, Map<StackGresVersion, List<String>>>
          orderedSupportedPostgresVersions) {
    this.configFinder = configFinder;
    this.supportedPostgresVersions = orderedSupportedPostgresVersions;
    this.errorPostgresMismatchUri = ErrorType.getErrorTypeUri(ErrorType.PG_VERSION_MISMATCH);
    this.errorForbiddenUpdateUri = ErrorType.getErrorTypeUri(ErrorType.FORBIDDEN_CR_UPDATE);
  }

  @Override
  @SuppressFBWarnings(value = "SF_SWITCH_NO_DEFAULT",
      justification = "False positive")
  public void validate(StackGresShardedClusterReview review) throws ValidationFailed {
    StackGresShardedCluster cluster = review.getRequest().getObject();

    if (cluster == null) {
      return;
    }

    String givenPgVersion = Optional.of(cluster.getSpec())
        .map(StackGresShardedClusterSpec::getPostgres)
        .map(StackGresClusterPostgres::getVersion)
        .orElse(null);
    String coordinatorPgConfig = Optional.of(cluster.getSpec())
        .map(StackGresShardedClusterSpec::getCoordinator)
        .map(StackGresShardedClusterCoordinator::getConfigurationsForCoordinator)
        .map(StackGresClusterConfigurations::getSgPostgresConfig)
        .orElse(null);
    String shardsPgConfig = Optional.of(cluster.getSpec())
        .map(StackGresShardedClusterSpec::getShards)
        .map(StackGresClusterSpec::getConfigurations)
        .map(StackGresClusterConfigurations::getSgPostgresConfig)
        .orElse(null);

    if (givenPgVersion == null || coordinatorPgConfig == null || shardsPgConfig == null) {
      return;
    }

    if (!isPostgresVersionSupported(cluster, givenPgVersion)) {
      final String message = "Unsupported postgres version " + givenPgVersion
          + ".  Supported postgres versions are: "
          + Seq.seq(supportedPostgresVersions.get(getPostgresFlavorComponent(cluster)))
          .toString(", ");
      fail(errorPostgresMismatchUri, message);
    }

    new CoordinatorPostgresConfigValidator(configFinder).validate(review);
    new ShardsPostgresConfigValidator(configFinder).validate(review);
    for (var overrideShard : Optional.ofNullable(review.getRequest().getObject())
        .map(StackGresShardedCluster::getSpec)
        .map(StackGresShardedClusterSpec::getShards)
        .map(StackGresShardedClusterShards::getOverrides)
        .map(Seq::seq)
        .map(seq -> seq.zipWithIndex().toList())
        .orElse(List.of())) {
      if (overrideShard.v1.getConfigurationsForShards() == null
          || overrideShard.v1.getConfigurationsForShards().getSgPostgresConfig() == null) {
        continue;
      }
      new ShardsOverridePostgresConfigValidator(
          configFinder,
          overrideShard.v2.intValue(),
          overrideShard.v1.getIndex()).validate(review);
    }

    String givenMajorVersion = getPostgresFlavorComponent(cluster).get(cluster)
        .getMajorVersion(givenPgVersion);
    String namespace = cluster.getMetadata().getNamespace();
    String username = review.getRequest().getUserInfo().getUsername();

    switch (review.getRequest().getOperation()) {
      case CREATE:
        if (getPostgresFlavorComponent(cluster) != StackGresComponent.BABELFISH
            && BUGGY_PG_VERSIONS.keySet().contains(givenPgVersion)) {
          fail(errorForbiddenUpdateUri, "Do not use PostgreSQL " + givenPgVersion + ". "
              + BUGGY_PG_VERSIONS.get(givenPgVersion));
        }
        validateAgainstConfiguration(givenMajorVersion, coordinatorPgConfig, namespace,
            "for coordinator");
        validateAgainstConfiguration(givenMajorVersion, shardsPgConfig, namespace,
            "for shards");
        for (var overrideShard : Optional.of(cluster.getSpec().getShards())
            .map(StackGresShardedClusterShards::getOverrides)
            .orElse(List.of())) {
          if (overrideShard.getConfigurationsForShards() == null
              || overrideShard.getConfigurationsForShards().getSgPostgresConfig() == null) {
            continue;
          }
          String overrideShardsPgConfig = overrideShard
              .getConfigurationsForShards().getSgPostgresConfig();
          validateAgainstConfiguration(givenMajorVersion, overrideShardsPgConfig, namespace,
              "for shard " + overrideShard.getIndex());
        }
        break;
      case UPDATE:
        StackGresShardedCluster oldCluster = review.getRequest().getOldObject();
        if (!Objects.equals(
            getPostgresFlavorComponent(cluster),
            getPostgresFlavorComponent(oldCluster))) {
          fail(errorForbiddenUpdateUri,
              "postgres flavor can not be changed");
        }

        String oldCoordinatorPgConfig = oldCluster.getSpec().getCoordinator()
            .getConfigurationsForCoordinator().getSgPostgresConfig();
        if (!oldCoordinatorPgConfig.equals(coordinatorPgConfig)) {
          validateAgainstConfiguration(givenMajorVersion, coordinatorPgConfig, namespace,
              "for coordinator");
        }
        String oldShardsPgConfig = oldCluster.getSpec().getShards()
            .getConfigurations().getSgPostgresConfig();
        if (!oldShardsPgConfig.equals(shardsPgConfig)) {
          validateAgainstConfiguration(givenMajorVersion, shardsPgConfig, namespace,
              "for shards");
        }
        for (var overrideShard : Optional.of(cluster.getSpec().getShards())
            .map(StackGresShardedClusterShards::getOverrides)
            .orElse(List.of())) {
          if (overrideShard.getConfigurationsForShards() == null
              || overrideShard.getConfigurationsForShards().getSgPostgresConfig() == null) {
            continue;
          }
          String overrideShardsPgConfig = overrideShard
              .getConfigurationsForShards().getSgPostgresConfig();
          String oldOverrideShardsPgConfig = Optional.of(oldCluster.getSpec().getShards())
              .map(StackGresShardedClusterShards::getOverrides)
              .stream()
              .flatMap(List::stream)
              .filter(oldOverride -> Objects.equals(
                  oldOverride.getIndex(),
                  overrideShard.getIndex()))
              .findFirst()
              .map(StackGresShardedClusterShard::getConfigurationsForShards)
              .map(StackGresShardedClusterShardConfigurations::getSgPostgresConfig)
              .orElse(oldCluster.getSpec().getShards()
                  .getConfigurations().getSgPostgresConfig());
          if (!oldOverrideShardsPgConfig.equals(overrideShardsPgConfig)) {
            validateAgainstConfiguration(givenMajorVersion, overrideShardsPgConfig, namespace,
                "for shard " + overrideShard.getIndex());
          }
        }

        long givenMajorVersionIndex = getPostgresFlavorComponent(cluster)
            .get(cluster).streamOrderedMajorVersions()
            .zipWithIndex()
            .filter(t -> t.v1.equals(givenMajorVersion))
            .map(Tuple2::v2)
            .findAny()
            .get();
        String oldPgVersion = oldCluster.getSpec().getPostgres().getVersion();
        String oldMajorVersion = getPostgresFlavorComponent(oldCluster)
            .get(cluster)
            .getMajorVersion(oldPgVersion);
        long oldMajorVersionIndex = getPostgresFlavorComponent(oldCluster)
            .get(cluster)
            .streamOrderedMajorVersions()
            .zipWithIndex()
            .filter(t -> t.v1.equals(oldMajorVersion))
            .map(Tuple2::v2)
            .findAny()
            .get();

        if (!oldPgVersion.equals(givenPgVersion)
            && !(
                StackGresUtil.isLocked(cluster)
                && username != null
                && isServiceAccountUsername(username)
                && Objects.equals(
                    StackGresUtil.getLockServiceAccount(cluster),
                    getServiceAccountFromUsername(username))
                )) {
          if (givenMajorVersionIndex != oldMajorVersionIndex) {
            fail(errorForbiddenUpdateUri,
                "to upgrade a major Postgres version, please create an SGDbOps operation"
                    + " with \"op: majorVersionUpgrade\" and the target postgres version.");
          } else {
            fail(errorForbiddenUpdateUri,
                "to upgrade a minor Postgres version, please create an SGDbOps operation"
                    + " with \"op: minorVersionUpgrade\" and the target postgres version.");
          }
        }
        break;
      default:
    }
  }

  private void validateAgainstConfiguration(String givenMajorVersion,
      String pgConfig, String namespace, String...messageSuffixes) throws ValidationFailed {
    Optional<StackGresPostgresConfig> postgresConfigOpt = configFinder
        .findByNameAndNamespace(pgConfig, namespace);

    if (postgresConfigOpt.isPresent()) {
      StackGresPostgresConfig postgresConfig = postgresConfigOpt.get();
      String pgVersion = postgresConfig.getSpec().getPostgresVersion();

      if (!pgVersion.equals(givenMajorVersion)) {
        final String message = "Invalid postgres version, must be "
            + pgVersion + " to use SGPostgresConfig " + pgConfig
            + (messageSuffixes.length == 0 ? ""
                : " " + Arrays.asList(messageSuffixes).stream().collect(Collectors.joining(" ")));
        fail(errorPostgresMismatchUri, message);
      }
    }
  }

  private boolean isPostgresVersionSupported(StackGresShardedCluster cluster, String version) {
    return supportedPostgresVersions.get(getPostgresFlavorComponent(cluster))
        .get(StackGresVersion.getStackGresVersion(cluster))
        .contains(version);
  }

  private class CoordinatorPostgresConfigValidator
      extends AbstractReferenceValidator<
        StackGresShardedCluster, StackGresShardedClusterReview, StackGresPostgresConfig> {

    private CoordinatorPostgresConfigValidator(
        CustomResourceFinder<StackGresPostgresConfig> configFinder) {
      super(configFinder);
    }

    @Override
    protected Class<StackGresPostgresConfig> getReferenceClass() {
      return StackGresPostgresConfig.class;
    }

    @Override
    protected String getReference(StackGresShardedCluster resource) {
      return Optional.ofNullable(resource.getSpec()
          .getCoordinator().getConfigurationsForCoordinator())
          .map(StackGresClusterConfigurations::getSgPostgresConfig)
          .orElse(null);
    }

    @Override
    protected boolean checkReferenceFilter(StackGresShardedClusterReview review) {
      return !Optional.ofNullable(review.getRequest().getDryRun()).orElse(false);
    }

    @Override
    protected void onNotFoundReference(String message) throws ValidationFailed {
      PostgresConfigValidator.this.fail(message);
    }

    @Override
    protected String getCreateNotFoundErrorMessage(String reference) {
      return HasMetadata.getKind(getReferenceClass())
          + " " + reference + " not found for coordinator";
    }

    @Override
    protected String getUpdateNotFoundErrorMessage(String reference) {
      return "Cannot update coordinator to "
          + HasMetadata.getKind(getReferenceClass()) + " "
          + reference + " because it doesn't exists";
    }
  }

  private class ShardsPostgresConfigValidator
      extends CoordinatorPostgresConfigValidator {

    private ShardsPostgresConfigValidator(
        CustomResourceFinder<StackGresPostgresConfig> configFinder) {
      super(configFinder);
    }

    @Override
    protected String getReference(StackGresShardedCluster resource) {
      return Optional.ofNullable(resource.getSpec()
          .getShards().getConfigurations())
          .map(StackGresClusterConfigurations::getSgPostgresConfig)
          .orElse(null);
    }

    @Override
    protected String getCreateNotFoundErrorMessage(String reference) {
      return HasMetadata.getKind(getReferenceClass())
          + " " + reference + " not found for shards";
    }

    @Override
    protected String getUpdateNotFoundErrorMessage(String reference) {
      return "Cannot update shards to "
          + HasMetadata.getKind(getReferenceClass()) + " "
          + reference + " because it doesn't exists";
    }
  }

  private class ShardsOverridePostgresConfigValidator
      extends CoordinatorPostgresConfigValidator {

    private final int index;
    private final Integer shardIndex;

    private ShardsOverridePostgresConfigValidator(
        CustomResourceFinder<StackGresPostgresConfig> configFinder,
        int index,
        Integer shardIndex) {
      super(configFinder);
      this.index = index;
      this.shardIndex = shardIndex;
    }

    @Override
    protected String getReference(StackGresShardedCluster resource) {
      return Optional.ofNullable(resource.getSpec()
          .getShards().getOverrides())
          .map(overrides -> overrides.get(index))
          .map(StackGresShardedClusterShard::getConfigurationsForShards)
          .map(StackGresClusterConfigurations::getSgPostgresConfig)
          .orElse(null);
    }

    @Override
    protected boolean checkReferenceFilter(StackGresShardedClusterReview review) {
      return super.checkReferenceFilter(review)
          && !Objects.equals(
              review.getRequest().getObject().getSpec()
              .getShards().getOverrides().get(index)
              .getConfigurationsForShards().getSgPostgresConfig(),
              Optional.ofNullable(review.getRequest().getOldObject())
              .map(StackGresShardedCluster::getSpec)
              .map(StackGresShardedClusterSpec::getShards)
              .map(StackGresClusterSpec::getConfigurations)
              .map(StackGresClusterConfigurations::getSgPostgresConfig)
              .orElse(null));
    }

    @Override
    protected String getCreateNotFoundErrorMessage(String reference) {
      return HasMetadata.getKind(getReferenceClass())
          + " " + reference + " not found for shards override " + shardIndex;
    }

    @Override
    protected String getUpdateNotFoundErrorMessage(String reference) {
      return "Cannot update shards override " + shardIndex + " to "
          + HasMetadata.getKind(getReferenceClass()) + " "
          + reference + " because it doesn't exists";
    }
  }

}
