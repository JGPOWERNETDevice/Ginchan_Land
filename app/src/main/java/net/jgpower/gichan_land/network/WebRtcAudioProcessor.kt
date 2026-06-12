package net.jgpower.gichan_land.network

import android.util.Log

/**
 * WebRTC APM Native-ready wrapper.
 *
 * 현재 파일은 "실제 WebRTC APM 네이티브 라이브러리 연결 준비 버전"입니다.
 *
 * 특징:
 * 1. Manager 쪽 호출 구조는 그대로 유지합니다.
 * 2. 기존 20ms PCM 프레임을 WebRTC APM에 맞게 내부에서 10ms 단위로 분할합니다.
 * 3. native libwebrtc_apm_jni.so 가 있으면 native APM 처리를 호출합니다.
 * 4. native lib가 아직 없으면 no-op으로 안전하게 통과합니다.
 *
 * 즉, 이 파일을 먼저 교체해도 기존 무전 기능은 깨지지 않습니다.
 * 실제 음질 개선은 webrtc_apm_jni native 구현과 libwebrtc APM 연결 후 발생합니다.
 */
class WebRtcAudioProcessor(
    private val sampleRate: Int,
    private val channels: Int,
    private val frameBytes: Int
) {
    private val tag = "WEBRTC_APM"

    private val bytesPerSample = 2
    private val frameSamples = frameBytes / bytesPerSample / channels

    /**
     * WebRTC APM은 보통 10ms 단위 처리가 기본입니다.
     *
     * 현재 무전 프레임:
     * sampleRate = 8000
     * frameBytes = 320
     * frameSamples = 160
     * duration = 20ms
     *
     * 따라서 내부에서 10ms x 2개로 나누어 처리합니다.
     */
    private val apmFrameSamples = sampleRate / 100
    private val apmFrameBytes = apmFrameSamples * channels * bytesPerSample

    private var nativeHandle: Long = 0L
    private var nativeEnabled = false

    init {
        nativeEnabled = NativeBridge.isAvailable

        if (nativeEnabled) {
            try {
                nativeHandle = NativeBridge.nativeCreate(
                    sampleRate,
                    channels
                )

                nativeEnabled = nativeHandle != 0L

                Log.d(
                    tag,
                    "WebRtcAudioProcessor native created handle=$nativeHandle sampleRate=$sampleRate channels=$channels frameBytes=$frameBytes apmFrameBytes=$apmFrameBytes"
                )
            } catch (e: Throwable) {
                nativeEnabled = false
                nativeHandle = 0L

                Log.e(tag, "native APM create failed. fallback no-op", e)
            }
        } else {
            Log.d(
                tag,
                "WebRtcAudioProcessor native library not loaded. fallback no-op sampleRate=$sampleRate channels=$channels frameBytes=$frameBytes apmFrameBytes=$apmFrameBytes"
            )
        }

        if (frameBytes % apmFrameBytes != 0) {
            Log.d(
                tag,
                "frameBytes is not multiple of 10ms APM frame. frameBytes=$frameBytes apmFrameBytes=$apmFrameBytes"
            )
        }
    }

    /**
     * 송신 마이크 PCM 처리 위치입니다.
     *
     * AudioRecord -> PCM -> processCaptureFrameInPlace() -> Opus encode -> UDP
     */
    fun processCaptureFrameInPlace(pcmBytes: ByteArray) {
        if (pcmBytes.size != frameBytes) {
            Log.d(
                tag,
                "capture frame size mismatch size=${pcmBytes.size} expected=$frameBytes"
            )
            return
        }

        if (!nativeEnabled || nativeHandle == 0L) {
            return
        }

        processByTenMsChunks(
            pcmBytes = pcmBytes,
            isCapture = true
        )
    }

    /**
     * 수신 재생 PCM을 AEC reference로 전달하는 위치입니다.
     *
     * UDP -> Opus decode -> processRenderFrame() -> Jitter buffer -> AudioTrack
     *
     * 실제 WebRTC APM에서는 이 reverse/render stream이 AEC reference 역할을 합니다.
     */
    fun processRenderFrame(pcmBytes: ByteArray) {
        if (pcmBytes.isEmpty()) {
            return
        }

        if (!nativeEnabled || nativeHandle == 0L) {
            return
        }

        processByTenMsChunks(
            pcmBytes = pcmBytes,
            isCapture = false
        )
    }

    private fun processByTenMsChunks(
        pcmBytes: ByteArray,
        isCapture: Boolean
    ) {
        if (apmFrameBytes <= 0) {
            return
        }

        var offset = 0

        while (offset + apmFrameBytes <= pcmBytes.size) {
            try {
                if (isCapture) {
                    NativeBridge.nativeProcessCapture(
                        nativeHandle,
                        pcmBytes,
                        offset,
                        apmFrameBytes,
                        sampleRate,
                        channels
                    )
                } else {
                    NativeBridge.nativeProcessRender(
                        nativeHandle,
                        pcmBytes,
                        offset,
                        apmFrameBytes,
                        sampleRate,
                        channels
                    )
                }
            } catch (e: Throwable) {
                Log.e(
                    tag,
                    if (isCapture) {
                        "native process capture failed. disable native APM"
                    } else {
                        "native process render failed. disable native APM"
                    },
                    e
                )

                nativeEnabled = false
                break
            }

            offset += apmFrameBytes
        }
    }

    fun release() {
        if (nativeHandle != 0L) {
            try {
                NativeBridge.nativeRelease(nativeHandle)
                Log.d(tag, "WebRtcAudioProcessor native released handle=$nativeHandle")
            } catch (e: Throwable) {
                Log.e(tag, "native APM release failed", e)
            } finally {
                nativeHandle = 0L
                nativeEnabled = false
            }
        } else {
            Log.d(tag, "WebRtcAudioProcessor released no-op")
        }
    }

    private object NativeBridge {
        val isAvailable: Boolean

        init {
            var loaded = false

            try {
                System.loadLibrary("webrtc_apm_jni")
                loaded = true
                Log.d("WEBRTC_APM", "webrtc_apm_jni loaded")
            } catch (e: Throwable) {
                Log.d("WEBRTC_APM", "webrtc_apm_jni not available yet. no-op mode")
            }

            isAvailable = loaded
        }

        external fun nativeCreate(
            sampleRate: Int,
            channels: Int
        ): Long

        external fun nativeProcessCapture(
            handle: Long,
            pcmBytes: ByteArray,
            offset: Int,
            length: Int,
            sampleRate: Int,
            channels: Int
        )

        external fun nativeProcessRender(
            handle: Long,
            pcmBytes: ByteArray,
            offset: Int,
            length: Int,
            sampleRate: Int,
            channels: Int
        )

        external fun nativeRelease(
            handle: Long
        )
    }
}
