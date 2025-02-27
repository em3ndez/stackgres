#!/bin/sh

RETRY_DELAY="${RETRY_DELAY:-1000}"

. "$LOCAL_BIN_SHELL_UTILS_PATH"

run() {
  set -e

  echo "Acquiring lock..."
  acquire_lock
  echo "Lock acquired"
  maintain_lock >> /tmp/try-lock 2>&1 &
  TRY_LOCK_PID=$!

  reconcile_backups &
  PID=$!

  set +e
  (
  set +x
  while (kill -0 "$PID" && kill -0 "$TRY_LOCK_PID") 2>/dev/null
  do
    true
  done
  )

  if kill -0 "$PID" 2>/dev/null
  then
    kill_with_childs "$PID"
    retry kubectl patch "$SHARDED_BACKUP_CRD_NAME" -n "$CLUSTER_NAMESPACE" "$SHARDED_BACKUP_NAME" --type json --patch '[
      {"op":"replace","path":"/status/process/failure","value":'"$({ printf 'Lock lost:\n'; cat /tmp/try-lock; } | to_json_string)"'}
      ]'
    cat /tmp/try-lock
    echo "Lock lost"
    return 1
  else
    kill_with_childs "$TRY_LOCK_PID"
    release_lock >> /tmp/try-lock 2>&1
    echo "Lock released"
    wait "$PID"
    EXIT_CODE="$?"
    if [ "$EXIT_CODE" != 0 ]
    then
      cat /tmp/backup-push
      echo "Backup failed"
      [ -n "$SCHEDULED_SHARDED_BACKUP_KEY" ] || sleep 20
      return 1
    fi
  fi
}

reconcile_backups() {
  set -e

  if [ -n "$SCHEDULED_SHARDED_BACKUP_KEY" ]
  then
    SHARDED_BACKUP_NAME="${SHARDED_CLUSTER_NAME}-$(date +%Y-%m-%d-%H-%M-%S --utc)"
  fi

  SHARDED_BACKUP_ALREADY_COMPLETED=false
  create_or_update_backup_cr
  if [ "$SHARDED_BACKUP_ALREADY_COMPLETED" = "true" ]
  then
    echo "Already completed backup. Nothing to do!"
    return
  fi

  set +e
  echo "Retrieving primary"
  get_primary_pod

  echo "Performing backup"
  do_backup
  echo "Backup completed"

  echo "Retain backups"
  get_backup_crs

  echo "Updating backup CR as completed"
  set_backup_completed
  echo "Backup CR updated as completed"

  echo "Reconcile backup CRs"
  reconcile_backup_crs
  echo "Reconciliation of backup CRs completed"
}

get_backup_crs() {
  SHARDED_BACKUP_CR_TEMPLATE="{{ range .items }}"
  SHARDED_BACKUP_CR_TEMPLATE="${SHARDED_BACKUP_CR_TEMPLATE}{{ .spec.sgShardedCluster }}"
  SHARDED_BACKUP_CR_TEMPLATE="${SHARDED_BACKUP_CR_TEMPLATE}:{{ .metadata.namespace }}"
  SHARDED_BACKUP_CR_TEMPLATE="${SHARDED_BACKUP_CR_TEMPLATE}:{{ .metadata.name }}"
  SHARDED_BACKUP_CR_TEMPLATE="${SHARDED_BACKUP_CR_TEMPLATE}:{{ with .status.process.status }}{{ . }}{{ end }}"
  SHARDED_BACKUP_CR_TEMPLATE="${SHARDED_BACKUP_CR_TEMPLATE}:{{ with .status.process.jobPod }}{{ . }}{{ end }}"
  SHARDED_BACKUP_CR_TEMPLATE="${SHARDED_BACKUP_CR_TEMPLATE}:{{ with .metadata.labels }}{{ with index . \"$SCHEDULED_SHARDED_BACKUP_KEY\" }}{{ . }}{{ end }}{{ end }}"
  SHARDED_BACKUP_CR_TEMPLATE="${SHARDED_BACKUP_CR_TEMPLATE}:{{ range .status.sgBackups }}{{ . }},{{ end }}"
  SHARDED_BACKUP_CR_TEMPLATE="${SHARDED_BACKUP_CR_TEMPLATE}{{ printf "'"\n"'" }}{{ end }}"
  retry kubectl get "$SHARDED_BACKUP_CRD_NAME" -n "$CLUSTER_NAMESPACE" \
    --template="$SHARDED_BACKUP_CR_TEMPLATE" > /tmp/all-sharded-backups-in-namespace
  grep "^$SHARDED_CLUSTER_NAME:" /tmp/all-sharded-backups-in-namespace > /tmp/sharded-backups-in-namespace
  true > /tmp/all-sharded-backups
  local CLUSTER_SHARDED_BACKUP_NAMESPACE
  for CLUSTER_SHARDED_BACKUP_NAMESPACE in $CLUSTER_SHARDED_BACKUP_NAMESPACES
  do
    retry kubectl get "$SHARDED_BACKUP_CRD_NAME" -n "$CLUSTER_SHARDED_BACKUP_NAMESPACE" \
      --template="$SHARDED_BACKUP_CR_TEMPLATE" >> /tmp/all-sharded-backups
  done
  grep "^$CLUSTER_NAMESPACE.$SHARDED_CLUSTER_NAME:" /tmp/all-sharded-backups > /tmp/sharded-backups-out-of-namespace
  cat /tmp/sharded-backups-in-namespace /tmp/sharded-backups-out-of-namespace > /tmp/sharded-backups

  retry kubectl get "$BACKUP_CRD_NAME" -n "$CLUSTER_NAMESPACE" \
    --template="{{ range .items }}{{ printf \"%s\n\" .metadata.name }}{{ end }}" > /tmp/all-backups-in-namespace
  cat /tmp/all-backups-in-namespace > /tmp/backups
}

create_or_update_backup_cr() {
  local SHARDED_BACKUP_MANAGED_LIFECYCLE=true
  if "$SHARDED_BACKUP_IS_PERMANENT"
  then
    SHARDED_BACKUP_MANAGED_LIFECYCLE=false
  fi
  SHARDED_BACKUP_STATUS_YAML="$(cat << SHARDED_BACKUP_STATUS_YAML_EOF
status:
  process:
    jobPod: "$POD_NAME"
SHARDED_BACKUP_STATUS_YAML_EOF
  )"

  if ! kubectl get "$SHARDED_BACKUP_CRD_NAME" -n "$CLUSTER_NAMESPACE" "$SHARDED_BACKUP_NAME" -o name >/dev/null 2>&1
  then
    echo "Creating backup CR"
    cat << EOF | tee > /tmp/backup-to-create
apiVersion: $SHARDED_BACKUP_CRD_APIVERSION
kind: $SHARDED_BACKUP_CRD_KIND
metadata:
  namespace: "$CLUSTER_NAMESPACE"
  name: "$SHARDED_BACKUP_NAME"
  annotations:
    $SCHEDULED_SHARDED_BACKUP_KEY: "$RIGHT_VALUE"
  labels:
    $SCHEDULED_SHARDED_BACKUP_KEY: "$RIGHT_VALUE"
    $SCHEDULED_SHARDED_BACKUP_JOB_NAME_KEY: "$SCHEDULED_SHARDED_BACKUP_JOB_NAME"
spec:
  sgShardedCluster: "$SHARDED_CLUSTER_NAME"
  managedLifecycle: $SHARDED_BACKUP_MANAGED_LIFECYCLE
$SHARDED_BACKUP_STATUS_YAML
EOF
    retry kubectl create -f /tmp/backup-to-create -o json > /tmp/created-sharded-backup
    SHARDED_BACKUP_UID="$(jq .metadata.uid /tmp/created-sharded-backup)"
  else
    if ! retry kubectl get "$SHARDED_BACKUP_CRD_NAME" -n "$CLUSTER_NAMESPACE" "$SHARDED_BACKUP_NAME" --template="{{ .status.process.status }}" \
      | grep -q "^$SHARDED_BACKUP_PHASE_COMPLETED$"
    then
      DRY_RUN_CLIENT=$(kubectl version --client=true -o json | jq -r 'if (.clientVersion.minor | tonumber) < 18 then "true" else "client" end')
      echo "Updating backup CR"
      {
        retry kubectl get "$SHARDED_BACKUP_CRD_NAME" -n "$CLUSTER_NAMESPACE" "$SHARDED_BACKUP_NAME" -o yaml
        printf '%s\n' "$SHARDED_BACKUP_STATUS_YAML"
      } | kubectl create --dry-run="$DRY_RUN_CLIENT" -f - -o json | tee /tmp/backup-to-patch
      retry kubectl patch "$SHARDED_BACKUP_CRD_NAME" -n "$CLUSTER_NAMESPACE" "$SHARDED_BACKUP_NAME" -o yaml \
        --type merge --patch-file /tmp/backup-to-patch
    else
      SHARDED_BACKUP_ALREADY_COMPLETED=true
    fi
  fi
}

get_primary_pod() {
  retry kubectl get pod -n "$CLUSTER_NAMESPACE" -l "${COORDINATOR_CLUSTER_LABELS},${PATRONI_ROLE_KEY}=${PATRONI_PRIMARY_ROLE}" -o name > /tmp/current-primary
  if [ ! -s /tmp/current-primary ]
  then
    retry kubectl patch "$SHARDED_BACKUP_CRD_NAME" -n "$CLUSTER_NAMESPACE" "$SHARDED_BACKUP_NAME" --type json --patch '[
      {"op":"replace","path":"/status/process/failure","value":"Unable to find coordinator primary, backup aborted"}
      ]'
    kubectl get pod -n "$CLUSTER_NAMESPACE" -l "${COORDINATOR_CLUSTER_LABELS}" >&2 || true
    echo "Unable to find cooridnator primary, backup aborted" > /tmp/backup-push
    exit 1
  fi

  echo "Primary is $(cat /tmp/current-primary)"
}

do_backup() {
  SHARDED_BACKUP_START_TIME="$(date_iso8601)"
  echo "Starting sharded backup at $SHARDED_BACKUP_START_TIME"
  printf %s "$SHARDED_BACKUP_START_TIME" > /tmp/current-start-time

  rm -f /tmp/current-backups
  local CLUSTER_NAME
  local BACKUP_NAME
  local BACKUP_MANAGED_LIFECYCLE=true
  if "$SHARDED_BACKUP_IS_PERMANENT"
  then
    BACKUP_MANAGED_LIFECYCLE=false
  fi
  for CLUSTER_NAME in $CLUSTER_NAMES
  do
    BACKUP_NAME="${SHARDED_BACKUP_NAME}-${CLUSTER_NAME#${SHARDED_CLUSTER_NAME}-}"
    echo "Creating $BACKUP_CRD_KIND $BACKUP_NAME for $CLUSTER_CRD_KIND $CLUSTER_NAME"
    echo "$BACKUP_NAME" >> /tmp/current-backups
    cat << EOF | tee /tmp/backup-to-create-backup
apiVersion: $BACKUP_CRD_APIVERSION
kind: $BACKUP_CRD_KIND
metadata:
  namespace: $CLUSTER_NAMESPACE
  name: $BACKUP_NAME
  ownerReferences:
  - apiVersion: $SHARDED_BACKUP_CRD_APIVERSION
    kind: $SHARDED_BACKUP_CRD_KIND
    name: $SHARDED_BACKUP_NAME
    uid: $SHARDED_BACKUP_UID
spec:
  sgCluster: $CLUSTER_NAME
  managedLifecycle: $BACKUP_MANAGED_LIFECYCLE
  timeout: $SHARDED_BACKUP_TIMEOUT
  reconciliationTimeout: $SHARDED_BACKUP_RECONCILIATION_TIMEOUT
  maxRetries: 0
EOF

    if ! retry kubectl replace --force -f /tmp/backup-to-create-backup > /tmp/backup-create-backup 2>&1
    then
      cat /tmp/backup-create-backup > /tmp/backup-push
      retry kubectl patch "$SHARDED_BACKUP_CRD_NAME" -n "$CLUSTER_NAMESPACE" "$SHARDED_BACKUP_NAME" --type json --patch '[
        {"op":"replace","path":"/status/process/failure","value":'"$({ printf 'Backup failed:\n'; cat /tmp/backup-create-backup; } | to_json_string)"'}
        ]'
      exit 1
    fi
    cat /tmp/backup-create-backup
  done

  BACKUP_COMPRESSED_SIZE=0
  BACKUP_UNCOMPRESSED_SIZE=0
  echo "Waiting for SGDBackup $(cat /tmp/current-backups | tr '\n' ' ' | tr -s ' ') to complete"
  touch /tmp/completed-backups
  while true
  do
    local COMPLETED=true
    for BACKUP_NAME in $(cat /tmp/current-backups)
    do
      if ! grep -qxF "$BACKUP_NAME" /tmp/completed-backups
      then
        BACKUP_STATUS="$(retry kubectl get "$BACKUP_CRD_NAME" -n "$CLUSTER_NAMESPACE" "$BACKUP_NAME" \
          --template='{{ .status.process.status }} {{ .status.backupInformation.size.compressed }} {{ .status.backupInformation.size.uncompressed }}')"
        if [ "x$BACKUP_STATUS" != x ] && ! printf %s "$BACKUP_STATUS" | grep -q "^\($BACKUP_PHASE_COMPLETED\|$BACKUP_PHASE_FAILED\) "
        then
          COMPLETED=false
          continue
        fi
        if [ "x$BACKUP_STATUS" = x ] || printf %s "$BACKUP_STATUS" | grep -q "^$BACKUP_PHASE_FAILED "
        then
          echo "Backup $BACKUP_NAME failed" > /tmp/backup-push
          retry kubectl patch "$SHARDED_BACKUP_CRD_NAME" -n "$CLUSTER_NAMESPACE" "$SHARDED_BACKUP_NAME" --type json --patch '[
            {"op":"replace","path":"/status/process/failure","value":'"$(printf 'Backup failed: Backup %s failed' "$BACKUP_NAME" | to_json_string)"'}
            ]'
          exit 1
        fi
        printf %s "$BACKUP_NAME" >> /tmp/completed-backups
        BACKUP_COMPRESSED_SIZE="$((BACKUP_COMPRESSED_SIZE + $(printf %s "$BACKUP_STATUS" | cut -d ' ' -f 2) ))"
        BACKUP_UNCOMPRESSED_SIZE="$((BACKUP_UNCOMPRESSED_SIZE + $(printf %s "$BACKUP_STATUS" | cut -d ' ' -f 3) ))"
        echo "...$BACKUP_NAME completed"
      fi
    done
    if "$COMPLETED"
    then
      break
    fi
    sleep 2
  done
  echo "...backup completed"
  printf %s "$BACKUP_COMPRESSED_SIZE" > /tmp/current-compressed-size
  printf %s "$BACKUP_UNCOMPRESSED_SIZE" > /tmp/current-uncompressed-size

  echo "Creating restore point $SHARDED_BACKUP_NAME"
  create_backup_restore_point

  SHARDED_BACKUP_END_TIME="$(date_iso8601)"
  echo "Sharded backup completed at $SHARDED_BACKUP_END_TIME (uncompressed: $BACKUP_UNCOMPRESSED_SIZE compressed: $BACKUP_COMPRESSED_SIZE)"
  printf %s "$SHARDED_BACKUP_END_TIME" > /tmp/current-end-time
}

create_backup_restore_point() {
  echo "Creating restore point $SHARDED_BACKUP_NAME"
  cat << EOF | { set +e; kubectl exec -i -n "$CLUSTER_NAMESPACE" "$(cat /tmp/current-primary)" -c "$PATRONI_CONTAINER_NAME" \
      -- sh -e $SHELL_XTRACE 2>&1; printf %s "$?" > /tmp/backup-restore-point-exit-code; } | tee /tmp/backup-restore-point
psql -q -d "$SHARDED_CLUSTER_DATABASE" -v ON_ERROR_STOP=1 \
$(
  if [ "$SHARDING_TYPE" = citus ]
  then
    cat << INNER_EOF
  -c "SELECT citus_create_restore_point('$SHARDED_BACKUP_NAME')"
INNER_EOF
  elif [ "$SHARDING_TYPE" = ddp ]
  then
    cat << INNER_EOF
  -c "SELECT ddp_create_restore_point('$SHARDED_BACKUP_NAME')"
INNER_EOF
  fi
)
EOF
  if [ "$(cat /tmp/backup-restore-point-exit-code)" != 0 ]
  then
    cat /tmp/backup-restore-point > /tmp/backup-push
    retry kubectl patch "$SHARDED_BACKUP_CRD_NAME" -n "$CLUSTER_NAMESPACE" "$SHARDED_BACKUP_NAME" --type json --patch '[
      {"op":"replace","path":"/status/process/failure","value":'"$({ printf 'Backup failed:\n'; cat /tmp/backup-restore-point; } | to_json_string)"'}
      ]'
    exit 1
  fi

  echo "Retrieving latest LSNs"
  cat << EOF | { set +e; kubectl exec -i -n "$CLUSTER_NAMESPACE" "$(cat /tmp/current-primary)" -c "$PATRONI_CONTAINER_NAME" \
      -- sh -e $SHELL_XTRACE 2>&1; printf %s "$?" > /tmp/backup-restore-point-lsns-exit-code; } | tee /tmp/backup-restore-point-lsns
psql -q -d "$SHARDED_CLUSTER_DATABASE" -t -A -v ON_ERROR_STOP=1 \
$(
  if [ "$SHARDING_TYPE" = citus ]
  then
    cat << INNER_EOF
  -c "SELECT r.file_name from pg_walfile_name_offset(pg_current_wal_lsn()) as r" \
  -c "SELECT run_command_on_workers(\\\$\\\$ SELECT r.file_name from pg_walfile_name_offset(pg_current_wal_lsn()) as r \\\$\\\$)"
INNER_EOF
  elif [ "$SHARDING_TYPE" = ddp ]
  then
    cat << INNER_EOF
  -c "SELECT r.file_name from pg_walfile_name_offset(pg_current_wal_lsn()) as r" \
  -c "SELECT result FROM pg_foreign_server, LATERAL (SELECT * FROM dblink(srvname, 'SELECT r.file_name from pg_walfile_name_offset(pg_current_wal_lsn()) as r') AS (result text)) AS _"
INNER_EOF
  fi
)
EOF
  if [ "$(cat /tmp/backup-restore-point-lsns-exit-code)" != 0 ]
  then
    cat /tmp/backup-restore-point-lsns > /tmp/backup-push
    retry kubectl patch "$SHARDED_BACKUP_CRD_NAME" -n "$CLUSTER_NAMESPACE" "$SHARDED_BACKUP_NAME" --type json --patch '[
      {"op":"replace","path":"/status/process/failure","value":'"$({ printf 'Backup failed:\n'; cat /tmp/backup-restore-point-lsns; } | to_json_string)"'}
      ]'
    exit 1
  fi

  echo "Creating checkpoint and rotate the WALs"
  cat << EOF | { set +e; kubectl exec -i -n "$CLUSTER_NAMESPACE" "$(cat /tmp/current-primary)" -c "$PATRONI_CONTAINER_NAME" \
      -- sh -e $SHELL_XTRACE 2>&1; printf %s "$?" > /tmp/backup-restore-point-checkpoint-exit-code; } | tee /tmp/backup-restore-point-checkpoint
psql -q -d "$SHARDED_CLUSTER_DATABASE" -v ON_ERROR_STOP=1 \
$(
  if [ "$SHARDING_TYPE" = citus ]
  then
    cat << INNER_EOF
  -c "CHECKPOINT" \
  -c "SELECT run_command_on_workers(\\\$\\\$ CHECKPOINT \\\$\\\$)" \
  -c "SELECT pg_switch_wal()" \
  -c "SELECT run_command_on_workers(\\\$\\\$ SELECT pg_switch_wal() \\\$\\\$)"
INNER_EOF
  elif [ "$SHARDING_TYPE" = ddp ]
  then
    cat << INNER_EOF
  -c "CHECKPOINT" \
  -c "SELECT result FROM pg_foreign_server, LATERAL (SELECT * FROM dblink(srvname, 'CHECKPOINT') AS (result text)) AS _" \
  -c "SELECT pg_switch_wal()" \
  -c "SELECT result FROM pg_foreign_server, LATERAL (SELECT * FROM dblink(srvname, 'SELECT pg_switch_wal()') AS (result text)) AS _"
INNER_EOF
  fi
)
EOF
  if [ "$(cat /tmp/backup-restore-point-checkpoint-exit-code)" != 0 ]
  then
    cat /tmp/backup-restore-point-checkpoint > /tmp/backup-push
    retry kubectl patch "$SHARDED_BACKUP_CRD_NAME" -n "$CLUSTER_NAMESPACE" "$SHARDED_BACKUP_NAME" --type json --patch '[
      {"op":"replace","path":"/status/process/failure","value":'"$({ printf 'Backup failed:\n'; cat /tmp/backup-restore-point-checkpoint; } | to_json_string)"'}
      ]'
    exit 1
  fi

  echo "Waiting for LSNs to change from:"
  echo
  cat /tmp/backup-restore-point-lsns
  echo
  while true
  do
    cat << EOF | { set +e; kubectl exec -i -n "$CLUSTER_NAMESPACE" "$(cat /tmp/current-primary)" -c "$PATRONI_CONTAINER_NAME" \
        -- sh -e $SHELL_XTRACE 2>&1; printf %s "$?" > /tmp/backup-restore-point-current-lnss-exit-code; } | tee /tmp/backup-restore-point-current-lnss
psql -q -d "$SHARDED_CLUSTER_DATABASE" -t -A -v ON_ERROR_STOP=1 \
$(
  if [ "$SHARDING_TYPE" = citus ]
  then
    cat << INNER_EOF
  -c "SELECT r.file_name from pg_walfile_name_offset(pg_switch_wal()) as r" \
  -c "SELECT run_command_on_workers(\\\$\\\$ SELECT r.file_name from pg_walfile_name_offset(pg_switch_wal()) as r \\\$\\\$)"
INNER_EOF
  elif [ "$SHARDING_TYPE" = ddp ]
  then
    cat << INNER_EOF
  -c "SELECT r.file_name from pg_walfile_name_offset(pg_switch_wal()) as r" \
  -c "SELECT result FROM pg_foreign_server, LATERAL (SELECT * FROM dblink(srvname, 'SELECT r.file_name from pg_walfile_name_offset(pg_switch_wal()) as r') AS (result text)) AS _"
INNER_EOF
  fi
)
EOF
    if [ "$(cat /tmp/backup-restore-point-current-lnss-exit-code)" != 0 ]
    then
      cat /tmp/backup-restore-point-current-lnss > /tmp/backup-push
      retry kubectl patch "$SHARDED_BACKUP_CRD_NAME" -n "$CLUSTER_NAMESPACE" "$SHARDED_BACKUP_NAME" --type json --patch '[
        {"op":"replace","path":"/status/process/failure","value":'"$({ printf 'Backup failed:\n'; cat /tmp/backup-restore-point-current-lnss; } | to_json_string)"'}
        ]'
      exit 1
    fi
    PREVIOUS_LSNS_CHANGED=true
    while read -r PREVIOUS_LSN
    do
      if grep -qxF "$PREVIOUS_LSN" /tmp/backup-restore-point-current-lnss
      then
        PREVIOUS_LSNS_CHANGED=false
        break
      fi
    done < /tmp/backup-restore-point-lsns
    if [ "$PREVIOUS_LSNS_CHANGED" = true ]
    then
      break
    fi
    sleep 5
  done
}

set_backup_completed() {
  cat << EOF | tee /tmp/backup-to-patch
[
  {"op":"replace","path":"/status/sgBackups","value":[$(
    cat /tmp/current-backups \
      | sed 's/^\(.*\)$/"\1"/' | tr '\n' ',' | sed 's/,$//'
  )]},
  {"op":"replace","path":"/status/process/failure","value":""},
  {"op":"replace","path":"/status/process/timing","value":{
      "stored":"$(date_iso8601)",
      "start":"$(cat /tmp/current-start-time)",
      "end":"$(cat /tmp/current-end-time)"
    }
  },
  {"op":"replace","path":"/status/backupInformation","value":{
      "postgresVersion":"$POSTGRES_VERSION",
      "size":{
        "uncompressed":$(cat /tmp/current-uncompressed-size),
        "compressed":$(cat /tmp/current-compressed-size)
      }
    }
  }
]
EOF
  retry kubectl patch "$SHARDED_BACKUP_CRD_NAME" -n "$CLUSTER_NAMESPACE" "$SHARDED_BACKUP_NAME" --type json --patch-file /tmp/backup-to-patch
}

reconcile_backup_crs() {
  retry kubectl get pod -n "$CLUSTER_NAMESPACE" \
    --template="{{ range .items }}{{ .metadata.name }}{{ printf "'"\n"'" }}{{ end }}" \
    > /tmp/pods
  for SHARDED_BACKUP in $(cat /tmp/sharded-backups)
  do
    SHARDED_BACKUP_CR_NAMESPACE="$(echo "$SHARDED_BACKUP" | cut -d : -f 2)"
    SHARDED_BACKUP_CR_NAME="$(echo "$SHARDED_BACKUP" | cut -d : -f 3)"
    SHARDED_BACKUP_PHASE="$(echo "$SHARDED_BACKUP" | cut -d : -f 4)"
    SHARDED_BACKUP_POD="$(echo "$SHARDED_BACKUP" | cut -d : -f 5)"
    SHARDED_BACKUP_SHEDULED_BACKUP="$(echo "$SHARDED_BACKUP" | cut -d : -f 6)"
    SHARDED_BACKUPS="$(echo "$SHARDED_BACKUP" | cut -d : -f 7 | tr ',' ' ')"
    MISSING_BACKUP=false
    for BACKUP in $SHARDED_BACKUPS
    do
      if ! grep -qxF "$BACKUP" /tmp/backups
      then
        MISSING_BACKUP=true
        break
      fi
    done
    if "$MISSING_BACKUP"
    then
      echo "Deleting backup CR $SHARDED_BACKUP_CR_NAME since a referenced $BACKUP_CRD_KIND is missing"
      retry kubectl delete "$SHARDED_BACKUP_CRD_NAME" -n "$SHARDED_BACKUP_CR_NAMESPACE" "$SHARDED_BACKUP_CR_NAME"
    fi
    # if backup CR is a scheduled backup, is marked as running, has no pod or pod
    # has been terminated, delete it
    if [ "$SHARDED_BACKUP_SHEDULED_BACKUP" = "$RIGHT_VALUE" ] \
      && [ "$SHARDED_BACKUP_PHASE" = "$SHARDED_BACKUP_PHASE_RUNNING" ] \
      && ([ -z "$SHARDED_BACKUP_POD" ] || ! grep -q "^$SHARDED_BACKUP_POD$" /tmp/pods)
    then
      echo "Deleting backup CR $SHARDED_BACKUP_CR_NAME since backup is running but pod does not exists"
      retry kubectl delete "$SHARDED_BACKUP_CRD_NAME" -n "$SHARDED_BACKUP_CR_NAMESPACE" "$SHARDED_BACKUP_CR_NAME"
    fi
  done
}

run
