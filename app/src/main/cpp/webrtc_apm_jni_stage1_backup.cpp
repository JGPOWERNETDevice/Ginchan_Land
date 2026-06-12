#include <jni.h>
#include <android/log.h>
#include <cstdint>

#define LOG_TAG "WEBRTC_APM_JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * Stage 1 native bridge.
 *
 * This file intentionally uses a safe no-op native implementation.
 * Purpose:
 * 1. Verify System.loadLibrary("webrtc_apm_jni") works.
 * 2. Verify Kotlin -> JNI -> Kotlin audio processing path works.
 * 3. Keep existing walkie-talkie audio behavior unchanged.
 *
 * Stage 2:
 * Replace ApmHandle internals with actual WebRTC AudioProcessing module.
 */
struct ApmHandle {
    int sample_rate;
    int channels;
};

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

    ApmHandle* handle = new ApmHandle();
    handle->sample_rate = sampleRate;
    handle->channels = channels;

    LOGD("nativeCreate sampleRate=%d channels=%d handle=%p",
         sampleRate,
         channels,
         handle);

    return reinterpret_cast<jlong>(handle);
}

extern "C"
JNIEXPORT void JNICALL
Java_net_jgpower_gichan_1land_network_WebRtcAudioProcessor_00024NativeBridge_nativeProcessCapture(
        JNIEnv* env,
        jobject thiz,
        jlong handle,
        jbyteArray pcmBytes,
        jint offset,
        jint length,
        jint sampleRate,
        jint channels
) {
    (void) thiz;
    (void) sampleRate;
    (void) channels;

    if (handle == 0 || pcmBytes == nullptr || length <= 0) {
        return;
    }

    jsize arrayLength = env->GetArrayLength(pcmBytes);

    if (offset < 0 || length < 0 || offset + length > arrayLength) {
        LOGE("nativeProcessCapture invalid range offset=%d length=%d arrayLength=%d",
             offset,
             length,
             arrayLength);
        return;
    }

    // no-op
    // Stage 2: call WebRTC APM ProcessStream here.
}

extern "C"
JNIEXPORT void JNICALL
Java_net_jgpower_gichan_1land_network_WebRtcAudioProcessor_00024NativeBridge_nativeProcessRender(
        JNIEnv* env,
        jobject thiz,
        jlong handle,
        jbyteArray pcmBytes,
        jint offset,
        jint length,
        jint sampleRate,
        jint channels
) {
    (void) thiz;
    (void) sampleRate;
    (void) channels;

    if (handle == 0 || pcmBytes == nullptr || length <= 0) {
        return;
    }

    jsize arrayLength = env->GetArrayLength(pcmBytes);

    if (offset < 0 || length < 0 || offset + length > arrayLength) {
        LOGE("nativeProcessRender invalid range offset=%d length=%d arrayLength=%d",
             offset,
             length,
             arrayLength);
        return;
    }

    // no-op
    // Stage 2: call WebRTC APM ProcessReverseStream here.
}

extern "C"
JNIEXPORT void JNICALL
Java_net_jgpower_gichan_1land_network_WebRtcAudioProcessor_00024NativeBridge_nativeRelease(
        JNIEnv* env,
        jobject thiz,
        jlong handle
) {
    (void) env;
    (void) thiz;

    if (handle == 0) {
        return;
    }

    ApmHandle* apmHandle = reinterpret_cast<ApmHandle*>(handle);

    LOGD("nativeRelease handle=%p sampleRate=%d channels=%d",
         apmHandle,
         apmHandle->sample_rate,
         apmHandle->channels);

    delete apmHandle;
}
