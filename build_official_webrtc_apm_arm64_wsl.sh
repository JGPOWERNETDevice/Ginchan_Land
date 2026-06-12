#!/usr/bin/env bash
set -euo pipefail

# Official WebRTC APM build preparation script.
#
# Run this on WSL2 Ubuntu or Linux.
#
# This script fetches official WebRTC source and attempts to build the
# //modules/audio_processing:audio_processing target for Android arm64.
#
# WebRTC build output naming/layout may differ by branch.
# After build, inspect out/apm_arm64/obj/modules/audio_processing/
# and copy the generated static library/dependency set into your Android project.

WORKDIR="${HOME}/webrtc_official"
DEPOT_TOOLS="${WORKDIR}/depot_tools"
CHECKOUT="${WORKDIR}/checkout"

mkdir -p "${WORKDIR}"

if [ ! -d "${DEPOT_TOOLS}" ]; then
  git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git "${DEPOT_TOOLS}"
fi

export PATH="${DEPOT_TOOLS}:$PATH"

mkdir -p "${CHECKOUT}"
cd "${CHECKOUT}"

if [ ! -d "src" ]; then
  fetch --nohooks webrtc_android
fi

cd src

gclient sync

gn gen out/apm_arm64 --args='target_os="android" target_cpu="arm64" is_debug=false is_component_build=false rtc_include_tests=false treat_warnings_as_errors=false'

autoninja -C out/apm_arm64 audio_processing

echo ""
echo "Build attempted."
echo "Inspect:"
echo "  ${CHECKOUT}/src/out/apm_arm64/obj/modules/audio_processing/"
echo ""
echo "You may need to copy not only libaudio_processing.a but also dependency archives."
echo "For simple app integration, prefer a validated prebuilt bundle if this official target outputs split archives."
