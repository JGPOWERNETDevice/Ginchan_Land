#!/usr/bin/env bash
set -euo pipefail

# Copy official WebRTC license and representative headers into an Android project.
#
# Usage:
#   ./copy_webrtc_apm_to_project.sh /path/to/webrtc/src /mnt/c/Users/devic/AndroidStudioProjects/Gichan_Land
#
# This script copies license files and broad header/source tree folders.
# It does NOT copy compiled libraries automatically because WebRTC build outputs
# can differ by revision/target.

WEBRTC_SRC="${1:-}"
PROJECT_ROOT="${2:-}"

if [ -z "${WEBRTC_SRC}" ] || [ -z "${PROJECT_ROOT}" ]; then
  echo "Usage: $0 /path/to/webrtc/src /path/to/Gichan_Land"
  exit 1
fi

DEST="${PROJECT_ROOT}/app/src/main/cpp/third_party/webrtc_apm"

mkdir -p "${DEST}/include"
mkdir -p "${DEST}/lib/arm64-v8a"
mkdir -p "${DEST}/licenses"

# License files.
cp -f "${WEBRTC_SRC}/LICENSE" "${DEST}/licenses/LICENSE" || true
cp -f "${WEBRTC_SRC}/PATENTS" "${DEST}/licenses/PATENTS" || true
cp -f "${WEBRTC_SRC}/AUTHORS" "${DEST}/licenses/AUTHORS" || true

# Representative include trees. This is intentionally broad because APM headers
# reference api/common_audio/rtc_base/system_wrappers headers.
rsync -a --include='*/' --include='*.h' --include='*.hpp' --exclude='*' "${WEBRTC_SRC}/api/" "${DEST}/include/api/" || true
rsync -a --include='*/' --include='*.h' --include='*.hpp' --exclude='*' "${WEBRTC_SRC}/modules/audio_processing/" "${DEST}/include/modules/audio_processing/" || true
rsync -a --include='*/' --include='*.h' --include='*.hpp' --exclude='*' "${WEBRTC_SRC}/common_audio/" "${DEST}/include/common_audio/" || true
rsync -a --include='*/' --include='*.h' --include='*.hpp' --exclude='*' "${WEBRTC_SRC}/rtc_base/" "${DEST}/include/rtc_base/" || true
rsync -a --include='*/' --include='*.h' --include='*.hpp' --exclude='*' "${WEBRTC_SRC}/system_wrappers/" "${DEST}/include/system_wrappers/" || true

echo "Copied headers/licenses to:"
echo "${DEST}"
echo ""
echo "Next required file:"
echo "${DEST}/lib/arm64-v8a/libwebrtc_audio_processing.a"
