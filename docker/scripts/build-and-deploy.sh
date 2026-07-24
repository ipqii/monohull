#!/bin/sh
set -eu

WORKSPACE_ROOT="/workspace"
MAXIMO_HOME="/opt/IBM/SMP/maximo"

if [ "${1:-}" != "" ]; then
    PROJECT_NAME="$1"
else
    PROJECT_COUNT=$(find "$WORKSPACE_ROOT" -mindepth 1 -maxdepth 1 -type d | wc -l)
    if [ "$PROJECT_COUNT" -ne 1 ]; then
        echo "ERROR: expected exactly one project directory under $WORKSPACE_ROOT (found $PROJECT_COUNT). Pass the project name as the first argument." >&2
        exit 1
    fi
    PROJECT_NAME=$(find "$WORKSPACE_ROOT" -mindepth 1 -maxdepth 1 -type d -printf '%f\n')
fi

PROJECT_DIR="$WORKSPACE_ROOT/$PROJECT_NAME"

if [ ! -d "$PROJECT_DIR" ]; then
    echo "ERROR: project directory not found: $PROJECT_DIR" >&2
    exit 1
fi

if [ ! -d "$MAXIMO_HOME" ]; then
    echo "ERROR: Maximo home not found: $MAXIMO_HOME" >&2
    exit 1
fi

echo "[build-and-deploy] project: $PROJECT_NAME"
echo "[build-and-deploy] running ant in $PROJECT_DIR"
cd "$PROJECT_DIR"
ant

ZIP_PATH=$(find "$WORKSPACE_ROOT" -mindepth 1 -maxdepth 1 -type f -name '*.zip' -printf '%T@ %p\n' \
    | sort -nr | head -n 1 | cut -d' ' -f2-)

if [ -z "$ZIP_PATH" ] || [ ! -f "$ZIP_PATH" ]; then
    echo "ERROR: ant completed but no zip was found in $WORKSPACE_ROOT" >&2
    exit 1
fi

echo "[build-and-deploy] deploying $ZIP_PATH -> $MAXIMO_HOME"
unzip -o "$ZIP_PATH" -d "$MAXIMO_HOME"

echo "[build-and-deploy] removing $ZIP_PATH"
rm -f "$ZIP_PATH"

echo "[build-and-deploy] done"
