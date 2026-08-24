#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
rm -rf out
mkdir -p out/main out/test
find src/main/java -name '*.java' -print > out/main-sources.txt
javac --release 17 -encoding UTF-8 -d out/main @out/main-sources.txt
find src/test/java -name '*.java' -print > out/test-sources.txt
javac --release 17 -encoding UTF-8 -cp out/main -d out/test @out/test-sources.txt
