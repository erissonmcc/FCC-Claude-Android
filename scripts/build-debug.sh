#!/data/data/com.termux/files/usr/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
set -a
. "$ROOT_DIR/.env"
set +a

export PATH="$JAVA_HOME/bin:$PATH"
if [ -n "${PREFIX:-}" ]; then
  mkdir -p "$HOME/.cache/fcc-termux-cmake-arm64/bin"
  cp "$ROOT_DIR/scripts/termux-cmake.sh" "$HOME/.cache/fcc-termux-cmake-arm64/bin/cmake"
  cp "$ROOT_DIR/scripts/termux-ninja.sh" "$HOME/.cache/fcc-termux-cmake-arm64/bin/ninja"
  chmod +x "$HOME/.cache/fcc-termux-cmake-arm64/bin/cmake" "$HOME/.cache/fcc-termux-cmake-arm64/bin/ninja"
fi
cd "$ROOT_DIR"
exec env -u LD_LIBRARY_PATH sh "$ROOT_DIR/gradlew" :app:assembleDebug \
  --console=plain \
  --no-parallel \
  --max-workers="${GRADLE_MAX_WORKERS:-6}" \
  -PreactNativeArchitectures="${REACT_NATIVE_ARCHITECTURES:-arm64-v8a}"
