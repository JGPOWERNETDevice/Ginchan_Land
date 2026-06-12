# CMake 연결 계획

현재 ChromiumOS standalone `webrtc_apm` 소스는 Bazel 기준으로 정리되어 있습니다.

Android Studio CMake에 바로 연결하려면 아래 의존성이 필요합니다.

핵심 wrapper:
- webrtc_apm/webrtc_apm.cc
- webrtc_apm/voice_activity_detector.cc
- webrtc_apm/cras_config/*.cc

핵심 WebRTC APM:
- api/audio/*
- api/environment/*
- api/task_queue/*
- modules/audio_processing/**/*
- common_audio/**/*
- rtc_base/**/*
- system_wrappers/**/*

외부 의존:
- abseil-cpp
- protobuf 일부
- iniparser
- metrics_library stub

따라서 바로 CMakeLists.txt를 교체하면 빌드 오류가 날 가능성이 큽니다.

추천 순서:

1. 현재 Stage 1 no-op JNI 유지
2. ChromiumOS source를 별도 static library로 먼저 빌드 시도
3. 성공한 static/shared lib를 app/src/main/cpp/third_party/webrtc_apm/lib/arm64-v8a에 배치
4. webrtc_apm_jni_minimal_wrapper.cpp 적용

현재 패키지는 4번의 JNI 파일을 미리 제공하는 것입니다.
