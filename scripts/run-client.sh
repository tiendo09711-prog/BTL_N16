#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
./scripts/build.sh
java -cp 'target/classes:target/dependency/*' vn.ptit.btl16.client.ClientMain
