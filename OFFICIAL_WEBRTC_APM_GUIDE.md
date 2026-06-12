# Official WebRTC APM build/add guide

This pack does not include WebRTC source or binaries.
Reason:
- Official WebRTC source is large.
- APM needs compiled native libraries, not only headers.
- The Android app must link ABI-specific native libraries.

Official sources:
- WebRTC source: https://webrtc.googlesource.com/src
- WebRTC APM docs: https://webrtc.googlesource.com/src/+/HEAD/modules/audio_processing/g3doc/audio_processing_module.md
- WebRTC license: https://webrtc.org/support/license
- WebRTC Android native docs: https://webrtc.googlesource.com/src/+/main/docs/native-code/android/

Recommended build environment:
- WSL2 Ubuntu or Linux build machine.
- Build Android arm64-v8a first.
- Add x86_64 later only if emulator testing is needed.

Your Android project expected location:

C:\Users\devic\AndroidStudioProjects\Gichan_Land

Your app native folder:

C:\Users\devic\AndroidStudioProjects\Gichan_Land\app\src\main\cpp

Expected third-party output layout after build:

app/src/main/cpp/third_party/webrtc_apm/
├─ include/
│  ├─ api/
│  ├─ modules/
│  ├─ common_audio/
│  ├─ rtc_base/
│  └─ system_wrappers/
├─ lib/
│  └─ arm64-v8a/
│     └─ libwebrtc_audio_processing.a
└─ licenses/
   ├─ LICENSE
   ├─ PATENTS
   ├─ AUTHORS
   └─ NOTICE

Important:
- Headers alone are not enough.
- CMake link should be enabled only after `lib/arm64-v8a/libwebrtc_audio_processing.a` exists.
