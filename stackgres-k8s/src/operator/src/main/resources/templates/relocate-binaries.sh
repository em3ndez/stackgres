#!/bin/sh

set -eu

mkdir -p "$PG_EXTENSIONS_PATH"

chmod 700 "$PG_EXTENSIONS_PATH"

mkdir -p "$PG_EXTENSIONS_BIN_PATH"

mkdir -p "$PG_EXTENSIONS_LIB_PATH"

mkdir -p "$PG_EXTENSIONS_EXTENSION_PATH"

mkdir -p "$PG_EXTENSIONS_LIB64_PATH"

if [ -d "$PG_RELOCATED_PATH" ]
then
  chmod -R u+rw "$PG_RELOCATED_PATH"
  rm -fr "$PG_RELOCATED_PATH"
fi
mkdir -p "$PG_RELOCATED_PATH"

if [ ! -f "$PG_RELOCATED_PATH/.done" ]
then
  for RELOCATE_PATH in "$PG_BIN_PATH:$PG_RELOCATED_BIN_PATH" \
    "$PG_LIB_PATH:$PG_RELOCATED_LIB_PATH" \
    "$PG_SHARE_PATH:$PG_RELOCATED_SHARE_PATH" \
    "$PG_LIB64_PATH:$PG_RELOCATED_LIB64_PATH"
  do
      echo "Relocating ${RELOCATE_PATH%:*} to ${RELOCATE_PATH#*:} ..."
      mkdir -p "${RELOCATE_PATH#*:}"
      cp -a -u "${RELOCATE_PATH%:*}/." "${RELOCATE_PATH#*:}"
      chmod -R 700 "${RELOCATE_PATH#*:}"
      echo "done."
  done
  touch "$PG_RELOCATED_PATH/.done"
else
  echo "$PG_RELOCATED_PATH already relocated, skipping"
fi

for EXTENSION_CONTROL_FILE in "$PG_EXTENSION_PATH"/*.control
do
  if ! [ -f "$EXTENSION_CONTROL_FILE" ]
  then
    continue
  fi
  EXTENSION_NAME="${EXTENSION_CONTROL_FILE%.*}"
  EXTENSION_NAME="${EXTENSION_NAME##*/}"
  echo "Relocating $EXTENSION_CONTROL_FILE (and $EXTENSION_NAME--*.sql) to $PG_EXTENSIONS_EXTENSION_PATH/. ..."
  cp -a -u "$EXTENSION_CONTROL_FILE" "${EXTENSION_CONTROL_FILE%.*}"--*.sql \
    "$PG_EXTENSIONS_EXTENSION_PATH/."
  echo "done."
done
