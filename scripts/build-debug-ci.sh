#!/usr/bin/env bash
set -uo pipefail

warmup_dir=$(mktemp -d)
trap 'rm -rf "$warmup_dir"' EXIT

for attempt in {1..10}; do
  warmup_log="$warmup_dir/metro-$attempt.log"
  set +e
  node ./node_modules/react-native/cli.js bundle \
    --platform android \
    --dev false \
    --minify false \
    --entry-file index.js \
    --bundle-output "$warmup_dir/index.android.bundle" \
    --assets-dest "$warmup_dir/assets" \
    --config metro.config.js \
    2>&1 | tee "$warmup_log"
  bundle_status=${PIPESTATUS[0]}
  set -e

  if [[ "$bundle_status" -eq 0 ]]; then
    break
  fi

  generated_worklet=$(sed -nE \
    's/.*Failed to get the SHA-1 for: (.*\/node_modules\/react-native-worklets\/\.worklets\/[0-9]+\.js)\.?$/\1/p' \
    "$warmup_log" | head -n 1)

  if [[ -z "$generated_worklet" || ! -f "$generated_worklet" ]]; then
    exit "$bundle_status"
  fi

  if [[ "$attempt" -eq 10 ]]; then
    echo "Metro still generated unindexed Worklets modules after $attempt bundle passes." >&2
    exit "$bundle_status"
  fi

  echo "Metro generated $generated_worklet during pass $attempt; starting another pass to index it."
done

gradle_command=(
  sh ./gradlew :app:assembleDebug
  --console=plain
  --no-daemon
  --max-workers=4
  -PreactNativeArchitectures=arm64-v8a
)
"${gradle_command[@]}"
