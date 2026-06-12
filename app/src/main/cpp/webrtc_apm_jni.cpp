#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cstdint>
#include <vector>

#include "webrtc_apm/webrtc_apm.h"

#define LOG_TAG "WEBRTC_APM_JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/*
 * Android minimal JNI wrapper for ChromiumOS standalone webrtc_apm.
 *
 * 초기 실험 설정:
 * - AEC ON
 * - NS  ON
 * - AGC OFF
 *
 * AGC는 음량 펌핑/출렁임을 만들 수 있어서 1차 테스트에서는 끕니다.
 */

namespace {

constexpr bool kEnableAec = true;
constexpr bool kEnableNs = true;
constexpr bool kEnableAgc = false;
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
    sample = std::max(-32768, std::min(32767, sample));
    return static_cast<int16_t>(sample);
}

static bool PrepareMonoFloat(ApmHandle* handle, const int16_t* input, int frames) {
    if (handle == nullptr || input == nullptr || frames <= 0) {
        return false;
    }

    if (handle->channels != 1) {
        LOGE("Only mono is supported. channels=%d", handle->channels);
        return false;
    }

    handle->float_buffer.resize(frames);
    handle->channel_ptrs.resize(1);

    for (int i = 0; i < frames; ++i) {
        handle->float_buffer[i] = Int16ToFloat(input[i]);
    }

    handle->channel_ptrs[0] = handle->float_buffer.data();
    return true;
}

static void CopyMonoFloatToInt16(const ApmHandle* handle, int16_t* output, int frames) {
    if (handle == nullptr || output == nullptr) {
        return;
    }

    for (int i = 0; i < frames; ++i) {
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

    if (sampleRate <= 0 || channels != 1) {
        LOGE("nativeCreate invalid args sampleRate=%d channels=%d", sampleRate, channels);
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

    if (offset < 0 || length < 0 || offset + length > arrayLength || length % 2 != 0) {
        LOGE("nativeProcessCapture invalid range offset=%d length=%d arrayLength=%d", offset, length, arrayLength);
        return;
    }

    jbyte* raw = env->GetByteArrayElements(pcmBytes, nullptr);
    if (raw == nullptr) {
        return;
    }

    auto* samples = reinterpret_cast<int16_t*>(raw + offset);
    const int frames = length / 2;

    if (PrepareMonoFloat(handle, samples, frames)) {
        int result = webrtc_apm_process_stream_f(
                handle->apm,
                handle->channels,
                handle->sample_rate,
                handle->channel_ptrs.data()
        );

        if (result != 0) {
            LOGE("webrtc_apm_process_stream_f failed result=%d", result);
        } else {
            CopyMonoFloatToInt16(handle, samples, frames);
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

    if (offset < 0 || length < 0 || offset + length > arrayLength || length % 2 != 0) {
        LOGE("nativeProcessRender invalid range offset=%d length=%d arrayLength=%d", offset, length, arrayLength);
        return;
    }

    jbyte* raw = env->GetByteArrayElements(pcmBytes, nullptr);
    if (raw == nullptr) {
        return;
    }

    auto* samples = reinterpret_cast<int16_t*>(raw + offset);
    const int frames = length / 2;

    if (PrepareMonoFloat(handle, samples, frames)) {
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
