<!--

Set title to:

```
Release StackGres 1.5.0-rc3
```

Generate template using the command:

```
sh stackgres-k8s/ci/utils/generate-release-template.sh $VERSION
```

-->

# Pre Checks

1. [ ] Make sure all tasks marked with label ~"target_version::1.5.0-rc3" are done.

# Release steps

1. [ ] Create local branch `release-1.5.0-rc3` from `main-1.5`:
    ```
    git checkout "main-1.5" && git pull && git checkout -b "release-1.5.0-rc3"
    ```
1. [ ] Update project version to `1.5.0-rc3`:
    ```
    sh -x stackgres-k8s/ci/utils/update-version.sh "1.5.0-rc3"
    ```
1. [ ] Update `CHANGELOG.md` (review commit messages to populate the changelog: `git log`)
1. [ ] Add 1.5.0-rc3 section in `doc/content/en/01-introduction/06-versions/_index.md` with values from `stackgres-k8s/src/common/src/main/resources/versions.properties`
1. [ ] Check the changes to ensure everything is correct before commit:
    ```
    git diff
    ```
1. [ ] Commit changes with message `version: 1.5.0-rc3`:
    ```
    git commit -S -a -m "version: 1.5.0-rc3"
    ```
1. [ ] Push `release-1.5.0-rc3` branch:

     **This step requires at least one ARM instance with docker installed and a gitlab runner registered with the StackGres project. All this setup is already built in a template. The only action we need to do is scale up the auto-scaling group `sg-army-builder` auto scaling group.** 

     ```
     for ASG in sg-army-builder; do aws --profile ongres --region us-east-1 autoscaling set-desired-capacity --desired-capacity 1 --auto-scaling-group-name "$ASG"; done
     ```

     **As an alternative approach [here](https://gitlab.com/snippets/1985684) is a handy snippet that allows to spin up such an instance in AWS.**
     ```
     # Remember to create a key pair called gitlab-runner in the target AWS region
     AMI="$(aws ssm get-parameters --names /aws/service/ami-amazon-linux-latest/amzn2-ami-hvm-arm64-gp2 --query "Parameters[].Value" --output text)"
     curl -s https://gitlab.com/snippets/1985684/raw | bash -s -- -r "" -t m6gd.4xlarge -i "" -d 14400 -df internal -dp /dev/nvme1n1 -rn army-builder -tl 'docker-junit-extension-runner, oci-image, ongresinc, stackgres-maven-runner, stackgres-native-build-runner, stackgres-quarkus-test-runner, stackgres-runner-v2, linux-arm64, stackgres-e2e-runner'
     ```

     Now we can push `release-1.5.0-rc3` branch and wait for the pipeline to complete:
    ```
    git push origin "release-1.5.0-rc3" -o ci.variable="DO_IMAGES=true" -o ci.variable="DO_NATIVE=true" -o ci.variable="DO_ARM=true"
    ```
1. [ ] Create tag `1.5.0-rc3`:
    ```
    git tag "1.5.0-rc3"
    ```
1. [ ] Push tag `1.5.0-rc3` to the origin and wait for the pipeline to complete:
    ```
    git push origin "1.5.0-rc3"
    ```
1. [ ] After pipeline succeeded, scale down the ARM runners (or terminate the instance created with the script):
    ```
     aws autoscaling update-auto-scaling-group      --auto-scaling-group-name sg-army-builder      --min-size 0      --max-size 0       --desired-capacity 0
    ```
1. [ ] Edit the [release notes of tag 1.5.0-rc3](https://gitlab.com/ongresinc/stackgres/-/releases/new?tag_name=1.5.0-rc3) by Copying and Pasting `CHANGELOG.md` section for version `1.5.0-rc3` (GitLab)
1. [ ] Merge local branch `release-1.5.0-rc3` into `main-1.5`:
    ```
    git checkout "main-1.5" && git pull && git merge --ff-only "release-1.5.0-rc3"
    ```
1. [ ] Update version to be `1.5.0-SNAPSHOT`:
    ```
    sh -x stackgres-k8s/ci/utils/update-version.sh "1.5.0-SNAPSHOT" "main-1.5"
    git commit -a -m "version: 1.5.0-SNAPSHOT"
    git push
    ```
1. [ ] Create branch `merge-1.5.0-rc3` from `main`:
    ```
    git checkout main && git pull && git checkout -b "merge-1.5.0-rc3"
    ```
1. [ ] Merge branch `main-1.5` into `merge-1.5.0-rc3`:
    ```
    git merge "main-1.5"
    ```
1. [ ] Push `merge-1.5.0-rc3` to origin, create the merge request to merge it into `main` and wait for the pipeline to complete fixing any encountered issues:
    ```
    git push origin "merge-1.5.0-rc3"
    ```

# Deploy Web

1. [ ] Checkout [stackgres-web](https://gitlab.com/ongresinc/web/stackgres) project
1. [ ] Checkout and update `development` branch:
    ```
    git checkout development && git pull
    ```
1. [ ] Set `STACKGRES_FULL_VERSIONS` in `.gitlab-ci.yml` by setting `1.5.0-rc3` as the first value.
1. [ ] Commit changes with message `version: 1.5.0-rc3`: `git commit -a -m 'version: 1.5.0-rc3'`
1. [ ] Push development to origin: `git push`
1. [ ] Check staging Web: `https://ongresinc.gitlab.io/web/stackgres/`
1. [ ] Merge `development` branch into `master`:
    ```
    git checkout master && git pull && git merge --ff-only development
    ```
1. [ ] Create tag `1.5.0-rc3`:
    ```
    git tag 1.5.0-rc3
    ```
1. [ ] Push master to origin:
    ```
    git push
    ```
1. [ ] Push tag `1.5.0-rc3` to origin:
    ```
    git push origin 1.5.0-rc3
    ```

# Post Checks

* Announcements:
  * [ ] Publish release on Announcement Slack channel
  * [ ] Publish release on Announcement Discord channel

# Changelog

~~~
# :rocket: Release 1.5.0-rc3 (${DATE})

## :notepad_spiral: NOTES

StackGres 1.5.0-rc3 is out! :confetti_ball: :champagne: 

So, what you are waiting for to try this release and have a look to the future of StackGres! 

## :sparkles: NEW FEATURES AND CHANGES

Nothing new here! :eyes:

## Web Console

Nothing new here! :eyes:

## :bug: FIXES

Nothing new here! :eyes:

## Web Console

Nothing new here! :eyes:

## :construction: KNOWN ISSUES

* Major version upgrade fails if some extensions version are not available for the target Postgres version ([#1368](https://gitlab.com/ongresinc/stackgres/-/issues/1368)) 
* Backups may be restored with inconsistencies when performed with a Postgres instance running on a different architecture ([#1539](https://gitlab.com/ongresinc/stackgres/-/issues/1539))

## :up: UPGRADE

To upgrade from a previous installation of the StackGres operator's helm chart you will have to upgrade the helm chart release.
 For more detailed information please refer to [our documentation](https://stackgres.io/doc/latest/install/helm/upgrade/#upgrade-operator).

To upgrade StackGres operator's (upgrade only works starting from 1.1 version or above) helm chart issue the following commands (replace namespace and release name if you used something different):

`helm upgrade -n "stackgres" "stackgres-operator" https://stackgres.io/downloads/stackgres-k8s/stackgres/1.5.0-rc3/helm/stackgres-operator.tgz`

> IMPORTANT: This release is incompatible with previous `alpha` or `beta` versions. Upgrading from those versions will require uninstalling completely StackGres including all clusters and StackGres CRDs (those in `stackgres.io` group) first.

Thank you for all the issues created, ideas, and code contributions by the StackGres Community!

## :twisted_rightwards_arrows: [FULL LIST OF COMMITS](https://gitlab.com/ongresinc/stackgres/-/commits/1.5.0-rc3)
~~~
