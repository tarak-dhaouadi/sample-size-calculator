#!/usr/bin/env bash
# Builds dist/TarakDhaouadiSampleSize.jar (needs a JDK 8 or newer on the PATH).
#   ./build.sh          compile and package
#   ./build.sh test     compile, package, then run the test suites
# The version stored in the JAR manifest can be set with:  VERSION=1.2.0 ./build.sh
set -euo pipefail
cd "$(dirname "$0")"

VERSION="${VERSION:-1.1.0}"
PKG=io/github/tarakdhaouadi/samplesize
MAIN=io.github.tarakdhaouadi.samplesize
JAR=dist/TarakDhaouadiSampleSize.jar

case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; *) SEP=":" ;; esac

rm -rf build dist
mkdir -p build/classes build/test-classes dist

echo "Compiling..."
javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 -d build/classes src/main/java/$PKG/*.java
cp -r src/main/resources/. build/classes/

printf 'Main-Class: %s.SampleSizeApp\nImplementation-Title: Tarak Dhaouadi for sample size\nImplementation-Version: %s\n' \
  "$MAIN" "$VERSION" > build/MANIFEST.MF
jar cfm "$JAR" build/MANIFEST.MF -C build/classes .
echo "Built $JAR (version $VERSION)"

if [ "${1:-}" = "test" ]; then
  echo "Compiling tests..."
  javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 -cp build/classes -d build/test-classes src/test/java/$PKG/*.java
  CP="build/classes${SEP}build/test-classes"
  echo "Running CalcTest..."
  java -Djava.awt.headless=true -cp "$CP" $MAIN.CalcTest
  echo "Running ModulesSmokeTest..."
  java -Djava.awt.headless=true -cp "$CP" $MAIN.ModulesSmokeTest
  echo "All tests passed."
fi
