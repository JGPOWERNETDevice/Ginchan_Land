#!/usr/bin/env bash
set -euo pipefail

# No-git download script for ChromiumOS standalone webrtc-apm.
#
# Run in WSL Ubuntu:
#
# chmod +x /mnt/c/Users/devic/AndroidStudioProjects/Gichan_Land/download_chromiumos_webrtc_apm_no_git.sh
# /mnt/c/Users/devic/AndroidStudioProjects/Gichan_Land/download_chromiumos_webrtc_apm_no_git.sh

PROJECT_ROOT="/mnt/c/Users/devic/AndroidStudioProjects/Gichan_Land"
CPP_ROOT="${PROJECT_ROOT}/app/src/main/cpp"
DEST_ROOT="${CPP_ROOT}/third_party/webrtc_apm"
UPSTREAM_DIR="${DEST_ROOT}/upstream_chromiumos_webrtc_apm"
ARCHIVE="/tmp/chromiumos_webrtc_apm_main.tar.gz"

mkdir -p "${DEST_ROOT}"
rm -rf "${UPSTREAM_DIR}"
mkdir -p "${UPSTREAM_DIR}"

echo "[1/4] Download ChromiumOS standalone webrtc-apm archive"
curl -L \
  "https://chromium.googlesource.com/chromiumos/third_party/webrtc-apm/+archive/refs/heads/main.tar.gz" \
  -o "${ARCHIVE}"

echo "[2/4] Extract archive"
tar -xzf "${ARCHIVE}" -C "${UPSTREAM_DIR}"

echo "[3/4] Copy license/readme references"
mkdir -p "${DEST_ROOT}/licenses"
if [ -f "${UPSTREAM_DIR}/README.md" ]; then
  cp -f "${UPSTREAM_DIR}/README.md" "${DEST_ROOT}/README_UPSTREAM_CHROMIUMOS_WEBRTC_APM.md"
fi

# This ChromiumOS mirror may not include all root WebRTC license files in a simple layout.
# Keep URL note for manual license preservation.
cat > "${DEST_ROOT}/licenses/UPSTREAM_SOURCE.txt" <<EOF
ChromiumOS standalone webrtc-apm source archive:
https://chromium.googlesource.com/chromiumos/third_party/webrtc-apm/+archive/refs/heads/main.tar.gz

Repository:
https://chromium.googlesource.com/chromiumos/third_party/webrtc-apm/

Use with WebRTC license notice:
https://webrtc.org/support/license
EOF

echo "[4/4] Done"
echo ""
echo "Downloaded source:"
echo "${UPSTREAM_DIR}"
echo ""
echo "Check these paths:"
echo "${UPSTREAM_DIR}/webrtc_apm"
echo "${UPSTREAM_DIR}/modules/audio_processing"
echo "${UPSTREAM_DIR}/common_audio"
echo "${UPSTREAM_DIR}/rtc_base"
echo ""
echo "Next: send me the file tree or zip if build wiring is needed."
