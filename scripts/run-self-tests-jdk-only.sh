#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
./scripts/compile-jdk-only.sh
java -cp 'out/main:out/test' vn.ptit.btl16.selftest.AllSelfTests
