#!/usr/bin/env sh
set -eu
echo '[INFO] Delegating legacy JDK-only name to dependency-aware self-tests.'
exec "$(dirname "$0")/run-self-tests.sh"
