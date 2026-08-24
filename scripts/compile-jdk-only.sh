#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
echo '[INFO] JavaFX/WebSocket require Maven dependencies; this legacy command now uses Maven.'
mvn -q test-compile dependency:copy-dependencies -DincludeScope=runtime
