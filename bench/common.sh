# Sourced by prepare.sh / run scripts. Resolves JDK 25 and repo paths.
BENCH_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(dirname "$BENCH_DIR")"
WORK_DIR="${WORK_DIR:-$BENCH_DIR/work}"
if [ -n "${JDK25_HOME:-}" ]; then export JAVA_HOME="$JDK25_HOME"; fi
export PATH="$JAVA_HOME/bin:$PATH"
# The sandbox injects proxy/trust-store flags through JAVA_TOOL_OPTIONS (Maven needs them); measured JVMs run via `env -u JAVA_TOOL_OPTIONS`.
JAVA_CLEAN="env -u JAVA_TOOL_OPTIONS java"
# Same GC/heap flags for every JVM variant.
JVM_FLAGS="-XX:+UseG1GC -XX:+UseCompactObjectHeaders -Xmx512m"
