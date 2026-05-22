#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../../.." && pwd)"
WEB_DIR="$ROOT_DIR/web"
ASSET_DIR="$ROOT_DIR/mobile/android/app/src/main/assets/web"

rm -rf "$ASSET_DIR"
mkdir -p "$ASSET_DIR"

rsync -a --delete \
  --exclude='.DS_Store' \
  --exclude='.git' \
  --exclude='node_modules' \
  "$WEB_DIR/" "$ASSET_DIR/"

echo "Synced web assets to $ASSET_DIR"
