#!/usr/bin/env bash
set -euo pipefail

LOG="${CI_BUILD_LOG:-build.log}"
./gradlew build --no-daemon >"$LOG" 2>&1 || {
  code=$?
  echo "::error::Gradle build failed (exit $code). Summary:"
  grep -E '^FAILURE:|^> Task .*FAILED|What went wrong:|Execution failed for task|BUILD FAILED|format violations:|Run .gradlew' "$LOG" | tail -40 || true
  echo ""
  echo "Last 80 lines (full log: artifact gradle-build-log / $LOG):"
  tail -80 "$LOG"
  exit "$code"
}

echo "Build OK. Task summary:"
grep -E '^> Task |BUILD SUCCESSFUL' "$LOG" | tail -25