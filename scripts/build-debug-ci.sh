#!/usr/bin/env bash
set -uo pipefail

gradle_command=(
  sh ./gradlew :app:assembleDebug
  --console=plain
  --no-daemon
  --max-workers=4
  -PreactNativeArchitectures=arm64-v8a
)
first_log=$(mktemp)
trap 'rm -f "$first_log"' EXIT

set +e
"${gradle_command[@]}" 2>&1 | tee "$first_log"
build_status=${PIPESTATUS[0]}
set -e

if [[ "$build_status" -eq 0 ]]; then
  exit 0
fi

generated_worklet=$(sed -nE \
  's/.*Failed to get the SHA-1 for: (.*\/node_modules\/react-native-worklets\/\.worklets\/[0-9]+\.js)\.?$/\1/p' \
  "$first_log" | head -n 1)

if [[ -z "$generated_worklet" || ! -f "$generated_worklet" ]]; then
  exit "$build_status"
fi

echo "Metro generated $generated_worklet during its first bundle pass; retrying Gradle with the generated module indexed."
exec "${gradle_command[@]}"
