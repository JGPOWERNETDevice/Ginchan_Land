cmake_minimum_required(VERSION 3.22.1)

project(webrtc_apm_android_experimental)

# Experimental direct-build CMake for ChromiumOS standalone webrtc-apm.
#
# 적용 위치:
# app/src/main/cpp/CMakeLists.txt
#
# 주의:
# 이 파일은 "실험용"입니다.
# ChromiumOS standalone webrtc-apm은 Bazel/ChromeOS 기준으로 정리되어 있어서
# Android CMake 직접 빌드는 누락 의존성 오류가 날 수 있습니다.
# 오류 로그를 보고 compat/stub 또는 source exclude를 추가하면서 조정합니다.

set(CPP_ROOT ${CMAKE_CURRENT_SOURCE_DIR})
set(UPSTREAM ${CPP_ROOT}/third_party/webrtc_apm/upstream_chromiumos_webrtc_apm)
set(COMPAT ${CPP_ROOT}/compat)

add_library(
        webrtc_apm_jni
        SHARED
        webrtc_apm_jni.cpp
)

find_library(
        log-lib
        log
)

# ChromiumOS standalone source.
file(GLOB_RECURSE WEBRTC_APM_CPP CONFIGURE_DEPENDS
        ${UPSTREAM}/api/*.cc
        ${UPSTREAM}/audio/*.cc
        ${UPSTREAM}/common_audio/*.cc
        ${UPSTREAM}/experiments/*.cc
        ${UPSTREAM}/modules/audio_processing/*.cc
        ${UPSTREAM}/modules/audio_coding/*.cc
        ${UPSTREAM}/rtc_base/*.cc
        ${UPSTREAM}/system_wrappers/*.cc
        ${UPSTREAM}/third_party/rnnoise/*.cc
        ${UPSTREAM}/webrtc_apm/*.cc
)

file(GLOB_RECURSE WEBRTC_APM_C CONFIGURE_DEPENDS
        ${UPSTREAM}/common_audio/*.c
        ${UPSTREAM}/third_party/pffft/*.c
)

set(WEBRTC_APM_SOURCES
        ${WEBRTC_APM_CPP}
        ${WEBRTC_APM_C}
)

# Exclude tests, benchmarks, fuzzers, mocks and aecdump tools.
list(FILTER WEBRTC_APM_SOURCES EXCLUDE REGEX ".*/test/.*")
list(FILTER WEBRTC_APM_SOURCES EXCLUDE REGEX ".*/tests/.*")
list(FILTER WEBRTC_APM_SOURCES EXCLUDE REGEX ".*_test\\.cc$")
list(FILTER WEBRTC_APM_SOURCES EXCLUDE REGEX ".*_unittest\\.cc$")
list(FILTER WEBRTC_APM_SOURCES EXCLUDE REGEX ".*_perf\\.cc$")
list(FILTER WEBRTC_APM_SOURCES EXCLUDE REGEX ".*benchmark.*")
list(FILTER WEBRTC_APM_SOURCES EXCLUDE REGEX ".*fuzzer.*")
list(FILTER WEBRTC_APM_SOURCES EXCLUDE REGEX ".*mock.*")
list(FILTER WEBRTC_APM_SOURCES EXCLUDE REGEX ".*aec_dump.*")
list(FILTER WEBRTC_APM_SOURCES EXCLUDE REGEX ".*unpack_aecdump.*")

# Minimal Android APM target: AEC + NS.
# Exclude the pitch/VAD path that pulls iSAC and standalone VAD symbols.
list(FILTER WEBRTC_APM_SOURCES EXCLUDE REGEX ".*/modules/audio_processing/vad/.*\\.cc$")

# Keep portable C/C++ first. SIMD files can be re-enabled later if needed.
list(FILTER WEBRTC_APM_SOURCES EXCLUDE REGEX ".*_sse.*")
list(FILTER WEBRTC_APM_SOURCES EXCLUDE REGEX ".*_avx.*")
list(FILTER WEBRTC_APM_SOURCES EXCLUDE REGEX ".*_mips.*")
list(FILTER WEBRTC_APM_SOURCES EXCLUDE REGEX ".*_neon.*")

# Minimal stubs for Android standalone APM build.


add_library(
        webrtc_apm_core
        STATIC
        ${WEBRTC_APM_SOURCES}
)

target_include_directories(
        webrtc_apm_core
        PRIVATE
        ${UPSTREAM}
        ${UPSTREAM}/third_party/pffft/src
        ${UPSTREAM}/third_party/rnnoise/src
        ${COMPAT}
)

target_include_directories(
        webrtc_apm_jni
        PRIVATE
        ${UPSTREAM}
        ${UPSTREAM}/third_party/pffft/src
        ${UPSTREAM}/third_party/rnnoise/src
        ${COMPAT}
)

target_compile_features(
        webrtc_apm_core
        PRIVATE
        cxx_std_20
)

target_compile_features(
        webrtc_apm_jni
        PRIVATE
        cxx_std_20
)

target_compile_definitions(
        webrtc_apm_core
        PRIVATE
        WEBRTC_POSIX
        WEBRTC_ANDROID
        WEBRTC_LINUX
        WEBRTC_APM_ANDROID_EXPERIMENTAL
        BYPASS_FEATURE_CHECK
        ABSL_USES_STD_STRING_VIEW=1
        WEBRTC_APM_DEBUG_DUMP=0
)

target_compile_definitions(
        webrtc_apm_jni
        PRIVATE
        WEBRTC_POSIX
        WEBRTC_ANDROID
        WEBRTC_LINUX
        WEBRTC_APM_ANDROID_EXPERIMENTAL
        BYPASS_FEATURE_CHECK
        ABSL_USES_STD_STRING_VIEW=1
        WEBRTC_APM_DEBUG_DUMP=0
)

target_compile_options(
        webrtc_apm_core
        PRIVATE
        -include ${COMPAT}/webrtc_apm_compat.h
        -Wno-unused-parameter
        -Wno-unused-variable
        -Wno-sign-compare
        -Wno-missing-field-initializers
)

target_compile_options(
        webrtc_apm_jni
        PRIVATE
        -include ${COMPAT}/webrtc_apm_compat.h
        -Wno-unused-parameter
        -Wno-unused-variable
        -Wno-sign-compare
        -Wno-missing-field-initializers
)

# Android 15+/16KB page-size compatibility.
# Keep this even when using NDK r27 or lower; NDK r28+ uses compatible
# alignment by default, but these flags make the JNI .so explicit.
target_link_options(
        webrtc_apm_jni
        PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384"
)

target_link_libraries(
        webrtc_apm_jni
        webrtc_apm_core
        ${log-lib}
)