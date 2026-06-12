# Copyright 2023 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

COPTS = [
    "-DWEBRTC_POSIX=1",
    "-DWEBRTC_LINUX=1",
    "-DWEBRTC_APM_DEBUG_DUMP=0",
    "-DWEBRTC_INTELLIGIBILITY_ENHANCER=0",
    "-DWEBRTC_NS_FLOAT=1",
    "-DWEBRTC_ENABLE_PROTOBUF=1",
    # Disable warnings.
    "-Wno-unknown-warning-option",
    "-Wno-deprecated-declarations",
    "-Wno-thread-safety-reference-return",  # system_wrappers/source/metrics.cc:68:44: error: returning variable 'info_' by reference requires holding mutex 'mutex_'
    "-Wno-return-type",  # gcc
    "-Wno-nonnull-compare",  # gcc
    "-Wno-address",  # gcc
] + select({
    "//:neon_build": ["-DWEBRTC_HAS_NEON"],
    "//conditions:default": [],
}) + select({
    "//:aarch32_build": ["-DWEBRTC_ARCH_ARM"],
    "//:aarch64_build": ["-DWEBRTC_ARCH_ARM64"],
    "//conditions:default": [],
})

AVX2_COPTS = ["-mavx2", "-mfma"]

def require_condition(cond):
    return select({
        cond: [],
        "//conditions:default": ["@platforms//:incompatible"],
    })
