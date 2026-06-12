# third_party/webrtc_apm

This directory is reserved for WebRTC APM source/header/binary files.

Current state:
- No actual WebRTC APM binary is included.
- The app can continue using the safe no-op JNI bridge.
- This directory only prepares a license-safe structure.

Expected final layout if using prebuilt static/shared libraries:

```text
third_party/webrtc_apm/
├─ include/
│  └─ ... WebRTC APM headers ...
├─ lib/
│  ├─ arm64-v8a/
│  │  └─ libwebrtc_audio_processing.a or .so
│  ├─ armeabi-v7a/
│  ├─ x86/
│  └─ x86_64/
└─ licenses/
   ├─ LICENSE
   ├─ PATENTS
   ├─ AUTHORS
   └─ NOTICE
```

Recommended ABI priority for field phones:
1. arm64-v8a
2. armeabi-v7a

Emulator-only ABI:
- x86
- x86_64

Do not enable linking in CMake until the actual include/lib files exist.
