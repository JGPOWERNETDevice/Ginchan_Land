# JNI implementation note

Do not replace `webrtc_apm_jni.cpp` with real APM calls until the following exist:

app/src/main/cpp/third_party/webrtc_apm/include/...
app/src/main/cpp/third_party/webrtc_apm/lib/arm64-v8a/libwebrtc_audio_processing.a
app/src/main/cpp/third_party/webrtc_apm/licenses/LICENSE
app/src/main/cpp/third_party/webrtc_apm/licenses/PATENTS
app/src/main/cpp/third_party/webrtc_apm/licenses/AUTHORS

Once those files exist, the no-op JNI can be replaced with code that includes:

#include "modules/audio_processing/include/audio_processing.h"

and creates/configures:

webrtc::AudioProcessing::Config
webrtc::AudioProcessingBuilder().Create()
ProcessStream(...)
ProcessReverseStream(...)

The exact API signature can vary by WebRTC revision, so the native implementation
must be matched to the selected official source revision.
