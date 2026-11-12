#!/usr/bin/env bash
set -euo pipefail
check_root="$(cd "$(dirname "$0")/.." && pwd)"
check_mode=unit
if (( $# > 0 )); then check_mode="$1"; fi
case "$check_mode" in unit|e2e|tools) ;; *) echo 'Usage: bash scripts/check.sh [unit|e2e|tools]' >&2; exit 2 ;; esac
check_java=java
check_javac=javac
check_java_home="$(printenv JAVA_HOME || true)"
if [[ -n "$check_java_home" ]]; then
  if command -v cygpath >/dev/null 2>&1; then check_java_home="$(cygpath -u "$check_java_home")"; fi
  check_java="$check_java_home/bin/java"
  check_javac="$check_java_home/bin/javac"
fi
command -v "$check_java" >/dev/null
command -v "$check_javac" >/dev/null
command -v node >/dev/null
command -v npm >/dev/null
check_java_major="$("$check_java" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p')"
[[ "$check_java_major" =~ ^[0-9]+$ ]] && (( check_java_major >= 17 )) || { echo 'JDK 17 or newer is required.' >&2; exit 1; }
node -e 'const [major,minor]=process.versions.node.split(".").map(Number); if(!((major===22&&minor>=18)||(major===24&&minor>=12)||major>24)){console.error("Use Node 22.18+ (22.x) or Node 24.12+");process.exit(1)}'
check_maven="$(printenv MVN || true)"
if [[ -z "$check_maven" ]]; then check_maven="$(command -v mvn || true)"; fi
run_maven() {
  if [[ -n "$check_maven" ]]; then "$check_maven" "$@"; else bash "$check_root/backend/mvnw" "$@"; fi
}
printf 'Java: %s\nNode: %s\n' "$check_java" "$(node -v)"
[[ "$check_mode" == tools ]] && exit 0
if [[ "$check_mode" == unit ]]; then
  (cd "$check_root/backend" && run_maven -B -q test)
fi
(cd "$check_root/frontend" && if [[ "$check_mode" == e2e ]]; then npm run test:e2e; else npm test; fi)
echo 'Checks passed.'
