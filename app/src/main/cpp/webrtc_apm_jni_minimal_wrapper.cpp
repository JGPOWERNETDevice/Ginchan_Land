#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cstdint>
#include <memory>
#include <vector>

#include "webrtc_apm/webrtc_apm.h"

#define LOG_TAG "WEBRTC_APM_JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/*
 * Minimal Android JNI wrapper for ChromiumOS standalone webrtc_apm.
 *
 * 목적:
 * - ChromiumOS webrtc_apm C wrapper를 앱 JNI에 직접 연결합니다.
 * - 전체 ChromiumOS config/metrics 흐름은 쓰지 않습니다.
 * - AEC / NS / AGC 중 먼저 AEC + NS를 켭니다.
 *
 * 주의:
 * - 이 파일은 libwebrtc_apm 또는 webrtc_apm 소스가 실제로 CMake에 연결된 뒤 사용합니다.
 * - 현재 Stage 1 no-op JNI가 정상인 상태에서는, lib 연결 전까지 이 파일로 교체하지 마세요.
 */

namespace {

constexpr bool kEnableAec = true;
constexpr bool kEnableNs = true;

// AGC는 음량이 출렁이거나 펌핑될 수 있으므로 첫 테스트에서는 끕니다.
// 필요하면 true로 바꿔서 2차 테스트합니다.
constexpr bool kEnableAgc = false;

// 현재 앱은 MODE_IN_COMMUNICATION + Jitter buffer 사용 중입니다.
// AEC 기준 delay는 기기마다 다르지만, 초기값은 60ms 정도가 무난합니다.
constexpr int kInitialStreamDelayMs = 60;

struct ApmHandle {
    webrtc_apm apm = nullptr;
    int sample_rate = 0;
    int channels = 0;
    std::vector<float> float_buffer;
    std::vector<float*> channel_ptrs;
};

static float Int16ToFloat(int16_t value) {
    return static_cast<float>(value) / 32768.0f;
}

static int16_t FloatToInt16(float value) {
    value = std::max(-1.0f, std::min(1.0f, value));
    int sample = static_cast<int>(value * 32767.0f);

    if (sample > 32767) sample = 32767;
    if (sample < -32768) sample = -32768;

    return static_cast<int16_t>(sample);
}

static bool PrepareDeinterleavedFloat(
        ApmHandle* handle,
        const int16_t* input,
        int frames_per_channel
) {
    if (handle == nullptr || input == nullptr) {
        return false;
    }

    if (handle->channels != 1) {
        LOGE("Only mono is supported in minimal wrapper. channels=%d", handle->channels);
        return false;
    }

    handle->float_buffer.resize(frames_per_channel);
    handle->channel_ptrs.resize(1);

    for (int i = 0; i < frames_per_channel; ++i) {
        handle->float_buffer[i] = Int16ToFloat(input[i]);
    }

    handle->channel_ptrs[0] = handle->float_buffer.data();
    return true;
}

static void CopyFloatToInt16(
        const ApmHandle* handle,
        int16_t* output,
        int frames_per_channel
) {
    if (handle == nullptr || output == nullptr || handle->float_buffer.empty()) {
        return;
    }

    for (int i = 0; i < frames_per_channel; ++i) {
        output[i] = FloatToInt16(handle->float_buffer[i]);
    }
}

}  // namespace

extern "C"
JNIEXPORT jlong JNICALL
Java_net_jgpower_gichan_1land_network_WebRtcAudioProcessor_00024NativeBridge_nativeCreate(
        JNIEnv* env,
        jobject thiz,
        jint sampleRate,
        jint channels
) {
    (void) env;
    (void) thiz;

    if (sampleRate <= 0 || channels <= 0) {
        LOGE("nativeCreate invalid args sampleRate=%d channels=%d", sampleRate, channels);
        return 0;
    }

    if (channels != 1) {
        LOGE("nativeCreate only mono supported for now. channels=%d", channels);
        return 0;
    }

    auto* handle = new ApmHandle();
    handle->sample_rate = sampleRate;
    handle->channels = channels;

    WebRtcApmConfig config = {};
    config.enforce_aec_on = kEnableAec;
    config.enforce_ns_on = kEnableNs;
    config.enforce_agc_on = kEnableAgc;
    config.aec3_fixed_capture_delay_samples = 0;

    handle->apm = webrtc_apm_create_with_enforced_effects(
            static_cast<unsigned int>(channels),
            static_cast<unsigned int>(sampleRate),
            nullptr,
            nullptr,
            &config
    );

    if (handle->apm == nullptr) {
        LOGE("webrtc_apm_create_with_enforced_effects failed");
        delete handle;
        return 0;
    }

    webrtc_apm_enable_effects(
            handle->apm,
            kEnableAec,
            kEnableNs,
            kEnableAgc
    );

    webrtc_apm_set_stream_delay(handle->apm, kInitialStreamDelayMs);

    LOGD(
            "nativeCreate REAL_APM sampleRate=%d channels=%d handle=%p aec=%d ns=%d agc=%d delayMs=%d",
            sampleRate,
            channels,
            handle,
            kEnableAec,
            kEnableNs,
            kEnableAgc,
            kInitialStreamDelayMs
    );

    return reinterpret_cast<jlong>(handle);
}

extern "C"
JNIEXPORT void JNICALL
Java_net_jgpower_gichan_1land_network_WebRtcAudioProcessor_00024NativeBridge_nativeProcessCapture(
        JNIEnv* env,
        jobject thiz,
        jlong handleValue,
        jbyteArray pcmBytes,
        jint offset,
        jint length,
        jint sampleRate,
        jint channels
) {
    (void) thiz;
    (void) sampleRate;
    (void) channels;

    auto* handle = reinterpret_cast<ApmHandle*>(handleValue);

    if (handle == nullptr || handle->apm == nullptr || pcmBytes == nullptr || length <= 0) {
        return;
    }

    jsize arrayLength = env->GetArrayLength(pcmBytes);

    if (offset < 0 || length < 0 || offset + length > arrayLength) {
        LOGE(
                "nativeProcessCapture invalid range offset=%d length=%d arrayLength=%d",
                offset,
                length,
                arrayLength
        );
        return;
    }

    if (length % 2 != 0) {
        LOGE("nativeProcessCapture length must be even. length=%d", length);
        return;
    }

    jbyte* raw = env->GetByteArrayElements(pcmBytes, nullptr);
    if (raw == nullptr) {
        return;
    }

    auto* samples = reinterpret_cast<int16_t*>(raw + offset);
    const int frames_per_channel = length / 2 / handle->channels;

    if (PrepareDeinterleavedFloat(handle, samples, frames_per_channel)) {
        int result = webrtc_apm_process_stream_f(
                handle->apm,
                handle->channels,
                handle->sample_rate,
                handle->channel_ptrs.data()
        );

        if (result != 0) {
            LOGE("webrtc_apm_process_stream_f failed result=%d", result);
        } else {
            CopyFloatToInt16(handle, samples, frames_per_channel);
        }
    }

    env->ReleaseByteArrayElements(pcmBytes, raw, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_net_jgpower_gichan_1land_network_WebRtcAudioProcessor_00024NativeBridge_nativeProcessRender(
        JNIEnv* env,
        jobject thiz,
        jlong handleValue,
        jbyteArray pcmBytes,
        jint offset,
        jint length,
        jint sampleRate,
        jint channels
) {
    (void) thiz;
    (void) sampleRate;
    (void) channels;

    auto* handle = reinterpret_cast<ApmHandle*>(handleValue);

    if (handle == nullptr || handle->apm == nullptr || pcmBytes == nullptr || length <= 0) {
        return;
    }

    jsize arrayLength = env->GetArrayLength(pcmBytes);

    if (offset < 0 || length < 0 || offset + length > arrayLength) {
        LOGE(
                "nativeProcessRender invalid range offset=%d length=%d arrayLength=%d",
                offset,
                length,
                arrayLength
        );
        return;
    }

    if (length % 2 != 0) {
        LOGE("nativeProcessRender length must be even. length=%d", length);
        return;
    }

    jbyte* raw = env->GetByteArrayElements(pcmBytes, nullptr);
    if (raw == nullptr) {
        return;
    }

    auto* samples = reinterpret_cast<int16_t*>(raw + offset);
    const int frames_per_channel = length / 2 / handle->channels;

    if (PrepareDeinterleavedFloat(handle, samples, frames_per_channel)) {
        int result = webrtc_apm_process_reverse_stream_f(
                handle->apm,
                handle->channels,
                handle->sample_rate,
                handle->channel_ptrs.data()
        );

        if (result != 0) {
            LOGE("webrtc_apm_process_reverse_stream_f failed result=%d", result);
        }
    }

    // Reverse stream is AEC reference only.
    // Do not write modified render PCM back to the playback buffer.
    env->ReleaseByteArrayElements(pcmBytes, raw, JNI_ABORT);
}

extern "C"
JNIEXPORT void JNICALL
Java_net_jgpower_gichan_1land_network_WebRtcAudioProcessor_00024NativeBridge_nativeRelease(
        JNIEnv* env,
        jobject thiz,
        jlong handleValue
) {
    (void) env;
    (void) thiz;

    auto* handle = reinterpret_cast<ApmHandle*>(handleValue);

    if (handle == nullptr) {
        return;
    }

    if (handle->apm != nullptr) {
        WebRtcApmStats stats = webrtc_apm_get_stats(handle->apm);

        LOGD(
                "nativeRelease REAL_APM handle=%p forward=%llu reverse=%llu",
                handle,
                static_cast<unsigned long long>(stats.forward_blocks_processed),
                static_cast<unsigned long long>(stats.reverse_blocks_processed)
        );

        webrtc_apm_destroy(handle->apm);
        handle->apm = nullptr;
    }

    delete handle;
}
