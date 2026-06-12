# WebRTC APM minimal wrapper stage 2

이 패키지는 ChromiumOS standalone `webrtc_apm` C wrapper를 사용하는
Android JNI 최소 wrapper 실험본입니다.

## 음질 기능

초기 설정:

```text
AEC = ON
NS  = ON
AGC = OFF
stream delay = 60ms
```

AGC는 음량 출렁임이 생길 수 있어서 첫 테스트에서는 꺼두었습니다.
노이즈 제거 목적이면 NS만 켜도 충분히 체감될 수 있습니다.

## 적용 방식

현재 앱 흐름:

```text
AudioRecord
→ PCM
→ WebRtcAudioProcessor.kt
→ webrtc_apm_jni.cpp
→ webrtc_apm_process_stream_f()
→ Opus
→ UDP
```

수신 흐름:

```text
UDP
→ Opus decode
→ PCM
→ webrtc_apm_process_reverse_stream_f()
→ Jitter buffer
→ AudioTrack
```

## 중요

이 파일은 `webrtc_apm` 라이브러리가 실제로 CMake에 연결된 뒤에만 교체하세요.

현재 Stage 1 no-op JNI 파일을 바로 교체하면 include/link 오류가 날 수 있습니다.

필요 조건:

```text
app/src/main/cpp/third_party/webrtc_apm/upstream_chromiumos_webrtc_apm/
```

이 소스가 있어야 하고, CMake가 해당 소스를 빌드하거나
별도 libwebrtc_apm.a/.so를 링크해야 합니다.

## 다음 단계

1. CMake로 ChromiumOS standalone webrtc_apm을 앱에 빌드 연결
2. 이 파일로 webrtc_apm_jni.cpp 교체
3. Rebuild
4. 로그 확인

정상 로그:

```text
nativeCreate REAL_APM sampleRate=8000 channels=1 ...
nativeRelease REAL_APM handle=... forward=... reverse=...
```
