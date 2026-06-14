#!/usr/bin/env bash
# Build atau ekstrak libstable_diffusion_core.so dari xororz/local-dream ke project lodu.
#
# Mode 1: ekstrak dari APK release (paling mudah)
#   ./scripts/build_sd_native.sh --from-apk
#
# Mode 2: build dari source (butuh NDK + QNN SDK + Rust)
#   export ANDROID_NDK_ROOT=/path/to/ndk
#   ./scripts/build_sd_native.sh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="${ROOT_DIR}/.sd-native-build"
LOCAL_DREAM_DIR="${BUILD_DIR}/local-dream"
LODU_JNI="${ROOT_DIR}/app/src/main/jniLibs/arm64-v8a"
LODU_QNN_ASSETS="${ROOT_DIR}/app/src/main/assets/qnnlibs"
APK_URL="https://github.com/xororz/local-dream/releases/download/v2.6.4/LocalDream_armv8a_2.6.4.apk"

extract_from_apk() {
  echo "==> Ekstrak native libs dari APK local-dream v2.6.4..."
  local apk="/tmp/LocalDream_armv8a.apk"
  curl -sL "$APK_URL" -o "$apk"
  mkdir -p "${LODU_JNI}" "${LODU_QNN_ASSETS}"
  unzip -o "$apk" "lib/arm64-v8a/libstable_diffusion_core.so" -d /tmp/local-dream-extract
  cp -f /tmp/local-dream-extract/lib/arm64-v8a/libstable_diffusion_core.so "${LODU_JNI}/"
  unzip -o "$apk" "assets/qnnlibs/*" -d /tmp/local-dream-extract
  cp -f /tmp/local-dream-extract/assets/qnnlibs/*.so "${LODU_QNN_ASSETS}/"
  echo "Selesai ekstrak dari APK."
}

if [[ "${1:-}" == "--from-apk" ]]; then
  extract_from_apk
else
  if [[ -z "${ANDROID_NDK_ROOT:-}" ]]; then
    echo "ANDROID_NDK_ROOT tidak diset. Gunakan --from-apk untuk ekstrak dari APK release:"
    echo "  ./scripts/build_sd_native.sh --from-apk"
    exit 1
  fi

  echo "==> Clone / update local-dream..."
  if [[ ! -d "${LOCAL_DREAM_DIR}" ]]; then
    git clone --depth 1 https://github.com/xororz/local-dream.git "${LOCAL_DREAM_DIR}"
  else
    git -C "${LOCAL_DREAM_DIR}" pull --ff-only || true
  fi

  CPP_DIR="${LOCAL_DREAM_DIR}/app/src/main/cpp"
  echo "==> Build native (arm64-v8a release)..."
  cd "${CPP_DIR}"
  export ANDROID_NDK_ROOT
  bash ./build.sh

  mkdir -p "${LODU_JNI}" "${LODU_QNN_ASSETS}"
  cp -f "${LOCAL_DREAM_DIR}/app/src/main/jniLibs/arm64-v8a/libstable_diffusion_core.so" "${LODU_JNI}/"
  cp -rf "${LOCAL_DREAM_DIR}/app/src/main/assets/qnnlibs/"* "${LODU_QNN_ASSETS}/" 2>/dev/null || true
fi

echo ""
echo "Selesai!"
echo "  Native core : ${LODU_JNI}/libstable_diffusion_core.so"
echo "  QNN assets  : ${LODU_QNN_ASSETS}/"
echo ""
echo "Langkah berikutnya:"
echo "  cd ${ROOT_DIR}"
echo "  ./gradlew :app:assembleRelease"
