#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mvn -q test-compile dependency:copy-dependencies -DincludeScope=runtime
java -cp 'target/classes:target/test-classes:target/dependency/*' vn.ptit.btl16.selftest.AllSelfTests
