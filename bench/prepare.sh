#!/usr/bin/env bash
# Builds every artifact the benchmark needs into bench/work/. Usage: JAVA_HOME=<jdk25> bench/prepare.sh [--native]
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
# MVN_ARGS=-o for an offline, pre-warmed ~/.m2 (the sandbox case)
MVN="mvn -B -ntp -q -DskipTests ${MVN_ARGS:-}"
mkdir -p "$WORK_DIR"
java -version 2>&1 | head -1

echo "== fake-eventhub"
(cd "$BENCH_DIR/fake-eventhub" && $MVN package dependency:build-classpath -Dmdep.outputFile=target/cp.txt)

echo "== Spring jar"
(cd "$REPO_DIR" && $MVN package)
cp "$REPO_DIR/target/live-ms-java.jar" "$WORK_DIR/live-ms-java.jar"

echo "== Spring AOT cache (extract + training run, same JVM flags as the measured runs)"
rm -rf "$WORK_DIR/spring-aot"
$JAVA_CLEAN -Djarmode=tools -jar "$WORK_DIR/live-ms-java.jar" extract --destination "$WORK_DIR/spring-aot"
(cd "$WORK_DIR/spring-aot" && $JAVA_CLEAN $JVM_FLAGS -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh \
   -Dspring.cloud.vault.enabled=false -jar live-ms-java.jar > "$WORK_DIR/spring-aot-train.log" 2>&1 || true)
ls -la "$WORK_DIR/spring-aot/app.aot"

echo "== Quarkus fast-jar"
(cd "$REPO_DIR/poc-quarkus" && $MVN package -Dquarkus.package.output-directory=quarkus-app)
rm -rf "$WORK_DIR/quarkus-jvm"; cp -a "$REPO_DIR/poc-quarkus/target/quarkus-app" "$WORK_DIR/quarkus-jvm"

echo "== Quarkus fast-jar + AOT cache (native Quarkus support, recording with the measured JVM flags)"
(cd "$REPO_DIR/poc-quarkus" && $MVN package -Dquarkus.package.output-directory=quarkus-app-aot \
   -Dquarkus.package.jar.aot.enabled=true -Dquarkus.package.jar.aot.phase=BUILD \
   "-Dquarkus.package.jar.aot.additional-recording-args=-XX:+UseCompactObjectHeaders,-XX:+UseG1GC,-Xmx512m")
rm -rf "$WORK_DIR/quarkus-aot"; cp -a "$REPO_DIR/poc-quarkus/target/quarkus-app-aot" "$WORK_DIR/quarkus-aot"
ls -la "$WORK_DIR/quarkus-aot/app.aot"

if [ "${1:-}" = "--native" ]; then
  echo "== Quarkus native (Mandrel container build)"
  (cd "$REPO_DIR/poc-quarkus" && $MVN package -Dnative -Dquarkus.native.container-build=true \
     -Dquarkus.native.builder-image=quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25)
  cp "$REPO_DIR/poc-quarkus/target/live-ms-quarkus-poc-0.0.1-SNAPSHOT-runner" "$WORK_DIR/quarkus-native"
fi
echo "done"
