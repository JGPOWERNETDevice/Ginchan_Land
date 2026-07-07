package net.jgpower.gichan_land.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.max
import net.jgpower.gichan_land.data.walkie.WalkieTarget
import net.jgpower.gichan_land.data.walkie.WalkieTargetType

object WalkieTalkieManager {

    private const val TAG = "WALKIE"

    private const val UDP_PORT = 60000
    private const val CHANNEL_ID = 1

    private const val MAGIC_1 = 0xAA.toByte()
    private const val MAGIC_2 = 0x55.toByte()
    private const val TYPE_AUDIO = 0x02.toByte()
    private const val CODEC_OPUS = 0x02.toByte()

    private const val TARGET_USER = 1
    private const val TARGET_GROUP = 2
    private const val TARGET_ALL = 3

    // Streamlit compatibility profile. Do not change Streamlit server audio parser.
    private const val STREAMLIT_SAMPLE_RATE = 8000
    private const val STREAMLIT_FRAME_DURATION_MS = 20
    private const val STREAMLIT_FRAME_SAMPLES = STREAMLIT_SAMPLE_RATE * STREAMLIT_FRAME_DURATION_MS / 1000
    private const val STREAMLIT_FRAME_BYTES = STREAMLIT_FRAME_SAMPLES * 2
    private const val STREAMLIT_OPUS_BITRATE = 16000

    // App-to-app 1:1 high quality profile.
    private const val APP_SAMPLE_RATE = 16000
    private const val APP_FRAME_DURATION_MS = 20
    private const val APP_FRAME_SAMPLES = APP_SAMPLE_RATE * APP_FRAME_DURATION_MS / 1000
    private const val APP_FRAME_BYTES = APP_FRAME_SAMPLES * 2
    private const val APP_OPUS_BITRATE = 24000

    private const val MAX_PAYLOAD_BYTES = 1024

    // App-to-app 16 kHz packets already carry redundancy, so keep latency moderate.
    private const val APP_JITTER_BUFFER_START_FRAMES = 6
    private const val APP_JITTER_BUFFER_MAX_FRAMES = 20
    private const val APP_PLAYBACK_MAX_PLC_FRAMES = 1

    // Streamlit bridge packets are 8 kHz raw Opus with no redundancy.
    // Keep this path shallow for field voice calls. The bridge may still deliver frames
    // with jitter, but a deep app buffer makes Bluetooth playback delay much worse.
    private const val STREAMLIT_JITTER_BUFFER_START_FRAMES = 3
    private const val STREAMLIT_JITTER_BUFFER_MAX_FRAMES = 15
    private const val STREAMLIT_PLAYBACK_MAX_PLC_FRAMES = 2

    private const val PLAYBACK_IDLE_SLEEP_MS = 1L

    // App-only payload marker. Streamlit packets stay raw Opus and never use this format.
    private const val REDUNDANT_PAYLOAD_MAGIC = 0x52 // 'R'
    private const val REDUNDANT_HISTORY_FRAMES = 5
    private const val REDUNDANT_MAX_FRAMES_PER_PACKET = REDUNDANT_HISTORY_FRAMES + 1

    // Streamlit stays 8k raw Opus. To reduce UDP broadcast loss without changing Streamlit parser,
    // send the exact same packet once more with the same sequence number.
    // If Streamlit does not ignore duplicate seq packets and audio repeats, set this to false.
    private const val STREAMLIT_DUPLICATE_SAME_SEQ_PACKET = false
    private const val STREAMLIT_DUPLICATE_DELAY_MS = 3L

    // Streamlit stays 8 kHz, but Redmi/legacy Bluetooth mic input can be too quiet.
    // Apply only before encoding 8 kHz raw Opus packets so Streamlit server code remains unchanged.
    private const val STREAMLIT_TX_GAIN = 2.4f
    private const val STREAMLIT_BT_TX_GAIN = 3.0f

    // bridge_server.py default monitor id. Packets to this worker must stay 8k raw Opus.
    private val STREAMLIT_WORKER_IDS = setOf("monitor", "streamlit", "bridge")

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var socket: DatagramSocket? = null

    @Volatile
    private var isReceiverRunning = false

    @Volatile
    private var isPlaybackRunning = false

    @Volatile
    private var isTransmitting = false

    @Volatile
    private var currentWorkerId: String? = null

    @Volatile
    private var currentAreaGroup: String? = null

    @Volatile
    private var currentTarget: WalkieTarget? = null

    @Volatile
    private var broadcastAddress: InetAddress? = null

    @Volatile
    private var streamlitDecoder: OpusCodec? = null

    @Volatile
    private var appDecoder: OpusCodec? = null

    @Volatile
    private var audioDeviceCallback: AudioDeviceCallback? = null

    @Volatile
    private var playbackTrack: AudioTrack? = null

    @Volatile
    private var suppressPlaybackUntilMs: Long = 0L

    private val audioRouteHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var playbackTrackSampleRate: Int = 0

    @Volatile
    private var lastPlaybackSampleRate: Int = STREAMLIT_SAMPLE_RATE

    @Volatile
    private var multicastLock: WifiManager.MulticastLock? = null

    @Volatile
    private var wifiLock: WifiManager.WifiLock? = null

    private val playbackLock = Object()
    private val streamlitDecoderLock = Object()
    private val appDecoderLock = Object()
    private val playbackQueue = ArrayDeque<PcmFrame>()

    private var sequence = 0

    private data class PcmFrame(
        val sampleRate: Int,
        val pcmBytes: ByteArray
    )

    private data class EncodedAudioFrame(
        val seq: Int,
        val opusBytes: ByteArray
    )

    private enum class AudioProfile(
        val sampleRate: Int,
        val frameBytes: Int,
        val bitrate: Int
    ) {
        STREAMLIT_COMPAT_8K(
            STREAMLIT_SAMPLE_RATE,
            STREAMLIT_FRAME_BYTES,
            STREAMLIT_OPUS_BITRATE
        ),
        APP_1TO1_HIGH_QUALITY_16K(
            APP_SAMPLE_RATE,
            APP_FRAME_BYTES,
            APP_OPUS_BITRATE
        )
    }

    private val transmitHistory = ArrayDeque<EncodedAudioFrame>()
    private val lastSeqBySender = mutableMapOf<String, Int>()

    // Temporary audio diagnostics for Galaxy USB debugging.
    // Search Logcat with: WALKIE_AUDIO_DIAG
    private const val AUDIO_DIAG_TAG = "WALKIE_AUDIO_DIAG"
    private const val AUDIO_DIAG_INTERVAL_MS = 1000L

    @Volatile
    private var streamlitRxLastLogAtMs = 0L

    @Volatile
    private var streamlitRxLastFrameAtMs = 0L

    @Volatile
    private var streamlitRxFrameCount = 0

    @Volatile
    private var streamlitRxBadPcmCount = 0

    @Volatile
    private var streamlitRxLargePayloadCount = 0

    @Volatile
    private var streamlitRxPayloadBytes = 0L

    @Volatile
    private var txLastLogAtMs = 0L

    @Volatile
    private var txFrameCount = 0

    @Volatile
    private var txPayloadBytes = 0L

    fun start(
        context: Context,
        workerId: String,
        areaGroup: String
    ) {
        appContext = context.applicationContext

        if (!ServerConfig.isWalkieNetworkAvailable(context)) {
            Log.d(TAG, "start blocked. walkie internal Wi-Fi unavailable")
            stop()
            return
        }

        currentWorkerId = workerId
        currentAreaGroup = areaGroup
        broadcastAddress = getBroadcastAddress(context)

        streamlitDecoder = OpusCodec(
            sampleRate = STREAMLIT_SAMPLE_RATE,
            channels = 1,
            frameBytes = STREAMLIT_FRAME_BYTES,
            bitrate = STREAMLIT_OPUS_BITRATE
        )

        appDecoder = OpusCodec(
            sampleRate = APP_SAMPLE_RATE,
            channels = 1,
            frameBytes = APP_FRAME_BYTES,
            bitrate = APP_OPUS_BITRATE
        )

        // Receive/playback should stay in normal media mode. Enter call mode only while transmitting.
        // Keeping MODE_IN_COMMUNICATION always enabled made Streamlit -> app playback choppy on Redmi/MIUI,
        // especially when a Bluetooth headset was connected.
        restoreReceivePlaybackMode(context)
        acquireWifiPacketLocks(context)
        registerAudioDeviceCallback(context)
        initPlaybackTrack(STREAMLIT_SAMPLE_RATE)
        startPlaybackLoop()

        if (isReceiverRunning && socket != null) {
            Log.d(TAG, "already started. workerId=$workerId areaGroup=$areaGroup")
            return
        }

        val newSocket = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            receiveBufferSize = 256 * 1024
            sendBufferSize = 256 * 1024
            bind(InetSocketAddress(UDP_PORT))
        }

        socket = newSocket
        startReceiver(newSocket)

        Log.d(
            TAG,
            "started workerId=$workerId areaGroup=$areaGroup streamlit=8k/raw app1to1=16k/redundancy5"
        )
        logAudioRouteSnapshot(context, "start")
    }

    fun stop() {
        stopTransmit()

        isReceiverRunning = false
        isPlaybackRunning = false

        synchronized(playbackLock) {
            playbackQueue.clear()
            playbackLock.notifyAll()
        }

        try {
            socket?.close()
        } catch (_: Exception) {
        }

        socket = null
        streamlitDecoder = null
        appDecoder = null
        synchronized(lastSeqBySender) {
            lastSeqBySender.clear()
        }
        synchronized(transmitHistory) {
            transmitHistory.clear()
        }

        synchronized(playbackLock) {
            try {
                playbackTrack?.stop()
            } catch (_: Exception) {
            }

            try {
                playbackTrack?.release()
            } catch (_: Exception) {
            }

            playbackTrack = null
            playbackTrackSampleRate = 0
        }

        releaseWifiPacketLocks()

        appContext?.let {
            unregisterAudioDeviceCallback(it)
            exitCommunicationMode(it)
        }
        appContext = null

        Log.d(TAG, "stopped")
    }

    fun setTarget(target: WalkieTarget?) {
        currentTarget = target
        Log.d(TAG, "target=$target")
    }

    fun isTransmitRunning(): Boolean {
        return isTransmitting
    }

    fun isStarted(): Boolean {
        return isReceiverRunning && socket != null
    }

    private fun resolveTransmitProfile(target: WalkieTarget): AudioProfile {
        // bridge_server.py currently expects raw Opus payload and defaults to receiver_worker_id="monitor".
        // Keep packets sent to the bridge/Streamlit as 8 kHz raw Opus so bridge_server.py and the
        // browser-side Streamlit audio code do not need to change.
        val targetWorkers = (target.targetWorkerId ?: "")
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }

        val isStreamlitBridgeTarget = targetWorkers.any { it in STREAMLIT_WORKER_IDS }

        return when {
            isStreamlitBridgeTarget -> AudioProfile.STREAMLIT_COMPAT_8K
            target.targetType == WalkieTargetType.USER -> AudioProfile.APP_1TO1_HIGH_QUALITY_16K
            else -> AudioProfile.STREAMLIT_COMPAT_8K
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireWifiPacketLocks(context: Context) {
        try {
            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            if (multicastLock == null) {
                multicastLock = wifiManager.createMulticastLock("walkie-udp-multicast-lock").apply {
                    setReferenceCounted(false)
                    acquire()
                }
                Log.d(TAG, "Wi-Fi multicast lock acquired")
            }

            if (wifiLock == null) {
                wifiLock = wifiManager.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "walkie-udp-high-perf-lock"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
                Log.d(TAG, "Wi-Fi high perf lock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "acquire Wi-Fi packet locks failed", e)
        }
    }

    private fun releaseWifiPacketLocks() {
        try {
            multicastLock?.let { lock ->
                if (lock.isHeld) lock.release()
            }
            Log.d(TAG, "Wi-Fi multicast lock released")
        } catch (e: Exception) {
            Log.e(TAG, "release multicast lock failed", e)
        } finally {
            multicastLock = null
        }

        try {
            wifiLock?.let { lock ->
                if (lock.isHeld) lock.release()
            }
            Log.d(TAG, "Wi-Fi high perf lock released")
        } catch (e: Exception) {
            Log.e(TAG, "release Wi-Fi lock failed", e)
        } finally {
            wifiLock = null
        }
    }

    @Suppress("DEPRECATION")
    private fun enterCommunicationMode(context: Context) {
        try {
            val audioManager =
                context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            Log.d(TAG, "audio mode set MODE_IN_COMMUNICATION")
        } catch (e: Exception) {
            Log.e(TAG, "enter communication mode failed", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun exitCommunicationMode(context: Context) {
        try {
            val audioManager =
                context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false

            Log.d(TAG, "audio mode restored MODE_NORMAL speakerOn=false SCO forced off")
        } catch (e: Exception) {
            Log.e(TAG, "exit communication mode failed", e)
        }
    }


    @Suppress("DEPRECATION")
    private fun restoreReceivePlaybackMode(context: Context) {
        val appCtx = context.applicationContext

        // BT SCO route changes are asynchronous. After MIC OFF, Galaxy/YYK-520 can report
        // SCO audio state 0 while AudioManager still exposes BT_SCO as the communication device.
        // Clear the route now and retry after the platform has had time to settle. During that
        // short window, avoid playing stale Streamlit frames into the narrow call route.
        suppressPlaybackUntilMs = SystemClock.elapsedRealtime() + 900L
        clearPlaybackQueue("restoreReceivePlaybackMode")
        releasePlaybackTrack("restoreReceivePlaybackMode")

        forceScoOffForReceive(appCtx, "restoreReceivePlaybackMode-afterScoOff-0ms")

        audioRouteHandler.postDelayed({
            forceScoOffForReceive(appCtx, "restoreReceivePlaybackMode-afterScoOff-300ms")
        }, 300L)

        audioRouteHandler.postDelayed({
            forceScoOffForReceive(appCtx, "restoreReceivePlaybackMode-afterScoOff-800ms")
            suppressPlaybackUntilMs = 0L
        }, 800L)
    }

    @Suppress("DEPRECATION")
    private fun forceScoOffForReceive(context: Context, label: String) {
        if (isTransmitting) {
            Log.d(AUDIO_DIAG_TAG, "skipScoOffReceive label=$label reason=transmitting")
            return
        }

        try {
            val audioManager =
                context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            audioManager.isSpeakerphoneOn = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }

            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false

            val btMediaAvailable = isBluetoothMediaOutputAvailable(context)
            Log.d(
                AUDIO_DIAG_TAG,
                "receiveOutputPolicy label=$label preferEarbud=true speakerFallback=false btMediaAvailable=$btMediaAvailable"
            )
            Log.d(TAG, "receive playback mode restored MODE_NORMAL media route, SCO forced off label=$label")
            logAudioRouteSnapshot(context, label)
        } catch (e: Exception) {
            Log.e(TAG, "force SCO off for receive failed label=$label", e)
        }
    }


    private fun isBluetoothMediaOutputAvailable(context: Context): Boolean {
        return try {
            val audioManager =
                context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun clearPlaybackQueue(reason: String) {
        synchronized(playbackLock) {
            val dropped = playbackQueue.size
            playbackQueue.clear()
            playbackLock.notifyAll()
            Log.d(AUDIO_DIAG_TAG, "playbackQueueClear reason=$reason dropped=$dropped")
        }
    }

    private fun releasePlaybackTrack(reason: String) {
        synchronized(playbackLock) {
            try {
                playbackTrack?.pause()
            } catch (_: Exception) {
            }

            try {
                playbackTrack?.flush()
            } catch (_: Exception) {
            }

            try {
                playbackTrack?.stop()
            } catch (_: Exception) {
            }

            try {
                playbackTrack?.release()
            } catch (_: Exception) {
            }

            playbackTrack = null
            playbackTrackSampleRate = 0
            Log.d(AUDIO_DIAG_TAG, "playbackTrackRelease reason=$reason")
        }
    }

    @SuppressLint("MissingPermission")
    fun startTransmit(context: Context): Boolean {
        if (!ServerConfig.isWalkieNetworkAvailable(context)) {
            Log.d(TAG, "startTransmit blocked. walkie internal Wi-Fi unavailable")
            stopTransmit()
            return false
        }

        val workerId = currentWorkerId
        val target = currentTarget
        val udpSocket = socket

        if (workerId.isNullOrBlank() || target == null || udpSocket == null) {
            Log.d(TAG, "startTransmit failed workerId=$workerId target=$target socket=$udpSocket")
            return false
        }

        val granted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            Log.d(TAG, "RECORD_AUDIO denied")
            return false
        }

        val requestedProfile = resolveTransmitProfile(target)

        if (isTransmitting) {
            return true
        }

        suppressPlaybackUntilMs = 0L
        isTransmitting = true

        thread(name = "walkie-transmit") {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            var audioRecord: AudioRecord? = null
            var encoder: OpusCodec? = null
            var echoCanceler: AcousticEchoCanceler? = null

            try {
                // Do Bluetooth SCO preparation inside the audio thread.
                // Redmi/MIUI Android 10 can block for several seconds while SCO is connecting;
                // doing it on the UI thread makes MIC ON/OFF look frozen.
                val bluetoothRouteReady = prepareAudioRouteForTransmit(context)
                val legacyBluetoothScoActive = bluetoothRouteReady && Build.VERSION.SDK_INT < Build.VERSION_CODES.S

                // Legacy SCO on Android 10/11 is often narrow-band 8 kHz only.
                // If we force 16 kHz AudioRecord while a Redmi Note8 headset SCO route is active,
                // some devices open the recorder but return silence. For those devices, fall back
                // to the 8 kHz raw Opus profile. App receivers already treat non-0x52 payloads as
                // 8 kHz raw Opus, so this does not require Streamlit/server changes.
                val profile = if (requestedProfile == AudioProfile.APP_1TO1_HIGH_QUALITY_16K && legacyBluetoothScoActive) {
                    Log.d(TAG, "legacy Bluetooth SCO active. USER transmit falls back to 8k raw Opus to avoid silent mic")
                    AudioProfile.STREAMLIT_COMPAT_8K
                } else {
                    requestedProfile
                }

                val minBuffer = AudioRecord.getMinBufferSize(
                    profile.sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val bluetoothActive = bluetoothRouteReady || isBluetoothCommunicationActive(context)
                val audioSource = if (legacyBluetoothScoActive) {
                    // Redmi Note8/MIUI Android 10 legacy SCO is a call-audio path.
                    // MIC source can open but return silence on some devices. Use VOICE_COMMUNICATION
                    // so the Bluetooth SCO headset microphone is selected by the platform.
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION
                } else if (bluetoothActive) {
                    MediaRecorder.AudioSource.VOICE_RECOGNITION
                } else {
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION
                }

                audioRecord = AudioRecord(
                    audioSource,
                    profile.sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    max(minBuffer, profile.frameBytes * 8)
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord init failed")
                    isTransmitting = false
                    return@thread
                }

                if (!bluetoothActive && AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(audioRecord.audioSessionId)
                    echoCanceler?.enabled = true
                    Log.d(TAG, "AcousticEchoCanceler enabled=${echoCanceler?.enabled}")
                } else {
                    Log.d(
                        TAG,
                        "AcousticEchoCanceler skipped bluetoothActive=$bluetoothActive available=${AcousticEchoCanceler.isAvailable()}"
                    )
                }

                encoder = OpusCodec(
                    sampleRate = profile.sampleRate,
                    channels = 1,
                    frameBytes = profile.frameBytes,
                    bitrate = profile.bitrate
                )

                val pcmFrame = ByteArray(profile.frameBytes)

                audioRecord.startRecording()

                Log.d(
                    TAG,
                    "transmit started requestedProfile=$requestedProfile effectiveProfile=$profile sampleRate=${profile.sampleRate} frameBytes=${profile.frameBytes} bluetoothActive=$bluetoothActive legacySco=$legacyBluetoothScoActive source=$audioSource"
                )
                logAudioRouteSnapshot(context, "transmitStarted")

                while (isTransmitting) {
                    val readOk = readFullFrame(
                        audioRecord = audioRecord,
                        buffer = pcmFrame
                    )

                    if (!readOk) {
                        continue
                    }

                    if (profile == AudioProfile.STREAMLIT_COMPAT_8K) {
                        val gain = if (bluetoothActive) STREAMLIT_BT_TX_GAIN else STREAMLIT_TX_GAIN
                        applyPcmGainInPlace(pcmFrame, gain)
                    }

                    val opusBytes = encoder.encodePcm(pcmFrame)

                    maybeLogTransmitFrame(
                        profile = profile,
                        pcmBytes = pcmFrame,
                        opusBytes = opusBytes,
                        bluetoothActive = bluetoothActive,
                        audioSource = audioSource
                    )

                    sendAudioPacket(
                        socket = udpSocket,
                        senderWorkerId = workerId,
                        target = target,
                        opusBytes = opusBytes,
                        profile = profile
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "transmit failed", e)
            } finally {
                try {
                    echoCanceler?.release()
                } catch (_: Exception) {
                }

                try {
                    audioRecord?.stop()
                } catch (_: Exception) {
                }

                audioRecord?.release()

                // Release call/SCO route after TX so Streamlit -> app receive playback uses the
                // normal media path instead of the narrow Bluetooth call path.
                appContext?.let { restoreReceivePlaybackMode(it) }

                Log.d(TAG, "transmit stopped")
            }
        }

        return true
    }

    fun stopTransmit() {
        isTransmitting = false
    }

    private fun readFullFrame(
        audioRecord: AudioRecord,
        buffer: ByteArray
    ): Boolean {
        var offset = 0

        while (offset < buffer.size && isTransmitting) {
            val read = audioRecord.read(
                buffer,
                offset,
                buffer.size - offset
            )

            if (read <= 0) {
                return false
            }

            offset += read
        }

        return offset == buffer.size
    }

    private fun applyPcmGainInPlace(buffer: ByteArray, gain: Float) {
        if (gain <= 1.0f) return

        var i = 0
        while (i + 1 < buffer.size) {
            val sample = (buffer[i].toInt() and 0xff) or (buffer[i + 1].toInt() shl 8)
            val signed = sample.toShort().toInt()
            val amplified = (signed * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

            buffer[i] = (amplified and 0xff).toByte()
            buffer[i + 1] = ((amplified shr 8) and 0xff).toByte()
            i += 2
        }
    }

    private fun sendAudioPacket(
        socket: DatagramSocket,
        senderWorkerId: String,
        target: WalkieTarget,
        opusBytes: ByteArray,
        profile: AudioProfile
    ) {
        try {
            if (opusBytes.isEmpty() || opusBytes.size > MAX_PAYLOAD_BYTES) {
                return
            }

            val payload = if (profile == AudioProfile.APP_1TO1_HIGH_QUALITY_16K) {
                val seq = sequence and 0xffff
                val currentFrame = EncodedAudioFrame(seq, opusBytes)
                val redundantPayload = buildRedundantPayload(currentFrame)

                rememberTransmitFrame(currentFrame)

                if (redundantPayload.size <= MAX_PAYLOAD_BYTES) {
                    redundantPayload
                } else {
                    // Keep the app 16k profile identifiable even when full history is too large.
                    // Do not fall back to raw 16k Opus because raw payload is reserved for Streamlit 8k.
                    buildRedundantPayload(currentFrame, includeHistory = false)
                }
            } else {
                opusBytes
            }

            val packetBytes = buildPacket(
                senderWorkerId = senderWorkerId,
                target = target,
                payload = payload
            )

            val address = broadcastAddress
                ?: InetAddress.getByName("255.255.255.255")

            val packet = DatagramPacket(
                packetBytes,
                packetBytes.size,
                address,
                UDP_PORT
            )

            socket.send(packet)

            if (profile == AudioProfile.STREAMLIT_COMPAT_8K && STREAMLIT_DUPLICATE_SAME_SEQ_PACKET) {
                try {
                    Thread.sleep(STREAMLIT_DUPLICATE_DELAY_MS)
                } catch (_: InterruptedException) {
                }
                socket.send(packet)
            }
        } catch (e: Exception) {
            Log.e(TAG, "send failed", e)
        }
    }

    private fun rememberTransmitFrame(frame: EncodedAudioFrame) {
        synchronized(transmitHistory) {
            transmitHistory.addLast(frame)
            while (transmitHistory.size > REDUNDANT_HISTORY_FRAMES) {
                transmitHistory.removeFirst()
            }
        }
    }

    private fun buildRedundantPayload(
        currentFrame: EncodedAudioFrame,
        includeHistory: Boolean = true
    ): ByteArray {
        val frames = ArrayList<EncodedAudioFrame>(REDUNDANT_MAX_FRAMES_PER_PACKET)

        if (includeHistory) {
            synchronized(transmitHistory) {
                frames.addAll(transmitHistory)
            }
        }
        frames.add(currentFrame)

        while (frames.size > REDUNDANT_MAX_FRAMES_PER_PACKET) {
            frames.removeAt(0)
        }

        val payloadSize = 2 + frames.sumOf { 4 + it.opusBytes.size }
        val payload = ByteArray(payloadSize)

        var index = 0
        payload[index++] = REDUNDANT_PAYLOAD_MAGIC.toByte()
        payload[index++] = frames.size.toByte()

        for (frame in frames) {
            val seq = frame.seq and 0xffff
            val length = frame.opusBytes.size

            payload[index++] = (seq and 0xff).toByte()
            payload[index++] = ((seq shr 8) and 0xff).toByte()
            payload[index++] = (length and 0xff).toByte()
            payload[index++] = ((length shr 8) and 0xff).toByte()

            System.arraycopy(frame.opusBytes, 0, payload, index, length)
            index += length
        }

        return payload
    }

    private fun buildPacket(
        senderWorkerId: String,
        target: WalkieTarget,
        payload: ByteArray
    ): ByteArray {
        val senderBytes = senderWorkerId.toByteArray(Charsets.UTF_8)
        val targetWorkerBytes =
            (target.targetWorkerId ?: "").toByteArray(Charsets.UTF_8)
        val targetGroupBytes =
            (target.targetAreaGroup ?: "").toByteArray(Charsets.UTF_8)

        val targetType = when (target.targetType) {
            WalkieTargetType.USER -> TARGET_USER
            WalkieTargetType.GROUP -> TARGET_GROUP
            WalkieTargetType.ALL -> TARGET_ALL
        }

        val headerSize =
            2 +
                    1 +
                    1 +
                    1 +
                    1 +
                    2 +
                    1 + senderBytes.size +
                    1 + targetWorkerBytes.size +
                    1 + targetGroupBytes.size +
                    2

        val packet = ByteArray(headerSize + payload.size + 2)

        var index = 0

        packet[index++] = MAGIC_1
        packet[index++] = MAGIC_2
        packet[index++] = TYPE_AUDIO
        packet[index++] = CODEC_OPUS
        packet[index++] = CHANNEL_ID.toByte()
        packet[index++] = targetType.toByte()

        val seq = sequence and 0xffff
        packet[index++] = (seq and 0xff).toByte()
        packet[index++] = ((seq shr 8) and 0xff).toByte()
        sequence = (sequence + 1) and 0xffff

        packet[index++] = senderBytes.size.toByte()
        System.arraycopy(senderBytes, 0, packet, index, senderBytes.size)
        index += senderBytes.size

        packet[index++] = targetWorkerBytes.size.toByte()
        System.arraycopy(targetWorkerBytes, 0, packet, index, targetWorkerBytes.size)
        index += targetWorkerBytes.size

        packet[index++] = targetGroupBytes.size.toByte()
        System.arraycopy(targetGroupBytes, 0, packet, index, targetGroupBytes.size)
        index += targetGroupBytes.size

        packet[index++] = (payload.size and 0xff).toByte()
        packet[index++] = ((payload.size shr 8) and 0xff).toByte()

        System.arraycopy(payload, 0, packet, index, payload.size)
        index += payload.size

        val crc = crc16Ccitt(
            data = packet,
            offset = 2,
            length = index - 2
        )

        packet[index++] = (crc and 0xff).toByte()
        packet[index] = ((crc shr 8) and 0xff).toByte()

        return packet
    }

    private fun startReceiver(socket: DatagramSocket) {
        isReceiverRunning = true

        thread(name = "walkie-receiver") {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val buffer = ByteArray(2048)

            Log.d(TAG, "receiver started")

            while (isReceiverRunning) {
                try {
                    val datagramPacket = DatagramPacket(buffer, buffer.size)
                    socket.receive(datagramPacket)

                    handleIncomingPacket(
                        data = datagramPacket.data,
                        size = datagramPacket.length
                    )
                } catch (e: Exception) {
                    if (isReceiverRunning) {
                        Log.e(TAG, "receive failed", e)
                    }
                }
            }

            Log.d(TAG, "receiver stopped")
        }
    }

    private fun handleIncomingPacket(
        data: ByteArray,
        size: Int
    ) {
        try {
            val packet = parsePacket(data, size) ?: return

            if (packet.codec != CODEC_OPUS.toInt()) return
            if (packet.channelId != CHANNEL_ID) return

            val myWorkerId = currentWorkerId ?: return
            val myAreaGroup = currentAreaGroup ?: return

            if (packet.senderWorkerId.isBlank()) return
            if (packet.senderWorkerId == myWorkerId) return

            val isForMe = when (packet.targetType) {
                WalkieTargetType.USER -> {
                    packet.targetWorkerId
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .contains(myWorkerId)
                }

                WalkieTargetType.GROUP -> {
                    packet.targetAreaGroup == myAreaGroup
                }

                WalkieTargetType.ALL -> {
                    true
                }
            }

            if (!isForMe) return
            if (packet.payload.isEmpty()) return
            if (packet.payload.size > MAX_PAYLOAD_BYTES) return

            val redundantFrames = parseRedundantPayload(packet.payload)
            if (redundantFrames != null) {
                for (frame in redundantFrames) {
                    if (frame.opusBytes.isEmpty()) continue
                    if (frame.opusBytes.size > MAX_PAYLOAD_BYTES) continue
                    if (shouldDropDuplicateOrOldPacket("${packet.senderWorkerId}:16k", frame.seq)) continue

                    val decodedPcm = synchronized(appDecoderLock) {
                        appDecoder?.decodeToPcm(frame.opusBytes)
                    } ?: continue
                    if (decodedPcm.isEmpty()) continue

                    enqueuePlayback(PcmFrame(APP_SAMPLE_RATE, decodedPcm))
                }
            } else {
                // Raw 8 kHz packets are used by the bridge/Streamlit path.
                // bridge_server.py does not send duplicate packets, and its sequence may reset when
                // the server/call restarts. Dropping old seq values here caused Streamlit -> app
                // playback to stutter or cut out after a restart, so raw 8 kHz playback accepts frames
                // in arrival order. App-only 16 kHz redundancy still uses seq duplicate filtering above.
                val decodedPcm = synchronized(streamlitDecoderLock) {
                    streamlitDecoder?.decodeToPcm(packet.payload)
                } ?: return
                if (decodedPcm.isEmpty()) return

                maybeLogStreamlitRxFrame(packet, decodedPcm)

                enqueuePlayback(PcmFrame(STREAMLIT_SAMPLE_RATE, decodedPcm))
            }
        } catch (e: Exception) {
            Log.e(TAG, "handle packet failed", e)
        }
    }

    private fun parseRedundantPayload(payload: ByteArray): List<EncodedAudioFrame>? {
        if (payload.size < 2) return null
        if ((payload[0].toInt() and 0xff) != REDUNDANT_PAYLOAD_MAGIC) return null

        val frameCount = payload[1].toInt() and 0xff
        if (frameCount <= 0 || frameCount > REDUNDANT_MAX_FRAMES_PER_PACKET) return null

        val frames = ArrayList<EncodedAudioFrame>(frameCount)
        var index = 2

        repeat(frameCount) {
            if (index + 4 > payload.size) return null

            val seq = (payload[index].toInt() and 0xff) or
                    ((payload[index + 1].toInt() and 0xff) shl 8)
            val length = (payload[index + 2].toInt() and 0xff) or
                    ((payload[index + 3].toInt() and 0xff) shl 8)
            index += 4

            if (length <= 0 || length > MAX_PAYLOAD_BYTES) return null
            if (index + length > payload.size) return null

            frames.add(EncodedAudioFrame(seq, payload.copyOfRange(index, index + length)))
            index += length
        }

        if (index != payload.size) return null

        return frames
    }

    private fun shouldDropDuplicateOrOldPacket(
        senderKey: String,
        seq: Int
    ): Boolean {
        synchronized(lastSeqBySender) {
            val lastSeq = lastSeqBySender[senderKey]

            if (lastSeq == null) {
                lastSeqBySender[senderKey] = seq
                return false
            }

            val diff = (seq - lastSeq + 65536) and 0xffff

            return when {
                diff == 0 -> true
                diff < 32768 -> {
                    if (diff > 1) {
                        Log.d(
                            TAG,
                            "packet gap sender=$senderKey last=$lastSeq seq=$seq lost=${diff - 1}"
                        )
                    }
                    lastSeqBySender[senderKey] = seq
                    false
                }
                else -> true
            }
        }
    }

    private fun initPlaybackTrack(sampleRate: Int) {
        synchronized(playbackLock) {
            if (playbackTrack != null && playbackTrackSampleRate == sampleRate) {
                return
            }

            try {
                playbackTrack?.stop()
            } catch (_: Exception) {
            }

            try {
                playbackTrack?.release()
            } catch (_: Exception) {
            }

            playbackTrack = null
            playbackTrackSampleRate = 0

            try {
                val frameBytes = if (sampleRate == APP_SAMPLE_RATE) APP_FRAME_BYTES else STREAMLIT_FRAME_BYTES
                val minBuffer = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build()
                    )
                    .setBufferSizeInBytes(playbackBufferSizeBytes(sampleRate, minBuffer))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .build()

                track.setVolume(AudioTrack.getMaxVolume())
                track.play()

                playbackTrack = track
                playbackTrackSampleRate = sampleRate

                Log.d(TAG, "playback track started MEDIA sampleRate=$sampleRate buffer=${playbackBufferSizeBytes(sampleRate, minBuffer)}")
                appContext?.let { logAudioRouteSnapshot(it, "playbackTrackStarted-$sampleRate") }
            } catch (e: Exception) {
                playbackTrack = null
                playbackTrackSampleRate = 0
                Log.e(TAG, "playback track init failed sampleRate=$sampleRate", e)
            }
        }
    }

    private fun playbackBufferSizeBytes(sampleRate: Int, minBuffer: Int): Int {
        val frameBytes = if (sampleRate == APP_SAMPLE_RATE) APP_FRAME_BYTES else STREAMLIT_FRAME_BYTES
        // 8 frames = about 160 ms for both 8 kHz and 16 kHz 20 ms frames.
        // The previous Streamlit value was about 600 ms and amplified delayed BT playback.
        val multiplier = 8
        return max(minBuffer, frameBytes * multiplier)
    }

    private fun jitterStartFramesFor(sampleRate: Int): Int {
        return if (sampleRate == STREAMLIT_SAMPLE_RATE) {
            STREAMLIT_JITTER_BUFFER_START_FRAMES
        } else {
            APP_JITTER_BUFFER_START_FRAMES
        }
    }

    private fun jitterMaxFramesFor(sampleRate: Int): Int {
        return if (sampleRate == STREAMLIT_SAMPLE_RATE) {
            STREAMLIT_JITTER_BUFFER_MAX_FRAMES
        } else {
            APP_JITTER_BUFFER_MAX_FRAMES
        }
    }

    private fun playbackMaxPlcFramesFor(sampleRate: Int): Int {
        return if (sampleRate == STREAMLIT_SAMPLE_RATE) {
            STREAMLIT_PLAYBACK_MAX_PLC_FRAMES
        } else {
            APP_PLAYBACK_MAX_PLC_FRAMES
        }
    }

    private fun enqueuePlayback(frame: PcmFrame) {
        val suppressUntil = suppressPlaybackUntilMs
        if (suppressUntil > 0L && SystemClock.elapsedRealtime() < suppressUntil) {
            Log.d(AUDIO_DIAG_TAG, "playbackSuppressed sampleRate=${frame.sampleRate} remainingMs=${suppressUntil - SystemClock.elapsedRealtime()}")
            return
        }

        synchronized(playbackLock) {
            val maxFrames = jitterMaxFramesFor(frame.sampleRate)
            while (playbackQueue.size >= maxFrames) {
                playbackQueue.removeFirst()
                Log.d(TAG, "playback queue overflow. drop oldest sampleRate=${frame.sampleRate}")
                Log.d(AUDIO_DIAG_TAG, "queueOverflow sampleRate=${frame.sampleRate} maxFrames=$maxFrames")
            }

            playbackQueue.addLast(frame)
            playbackLock.notifyAll()
        }
    }

    private fun maybeLogStreamlitRxFrame(
        packet: WalkieAudioPacket,
        decodedPcm: ByteArray
    ) {
        val now = SystemClock.elapsedRealtime()
        val dtMs = if (streamlitRxLastFrameAtMs == 0L) -1L else now - streamlitRxLastFrameAtMs
        streamlitRxLastFrameAtMs = now

        streamlitRxFrameCount += 1
        streamlitRxPayloadBytes += packet.payload.size.toLong()

        if (decodedPcm.size != STREAMLIT_FRAME_BYTES) {
            streamlitRxBadPcmCount += 1
        }

        if (packet.payload.size > 200) {
            streamlitRxLargePayloadCount += 1
        }

        val shouldLogNow =
            streamlitRxLastLogAtMs == 0L || now - streamlitRxLastLogAtMs >= AUDIO_DIAG_INTERVAL_MS

        if (!shouldLogNow) return

        val queueSize = synchronized(playbackLock) { playbackQueue.size }
        val avgPayload = if (streamlitRxFrameCount > 0) {
            streamlitRxPayloadBytes / streamlitRxFrameCount
        } else {
            0L
        }

        Log.d(
            AUDIO_DIAG_TAG,
            "streamlitRx sender=${packet.senderWorkerId} seq=${packet.seq} " +
                    "payloadLen=${packet.payload.size} avgPayload=$avgPayload head=${packet.payload.headHex(12)} " +
                    "pcmLen=${decodedPcm.size} expectedPcm=$STREAMLIT_FRAME_BYTES dtMs=$dtMs " +
                    "frames=$streamlitRxFrameCount badPcm=$streamlitRxBadPcmCount largePayload=$streamlitRxLargePayloadCount " +
                    "queue=$queueSize jitterStart=$STREAMLIT_JITTER_BUFFER_START_FRAMES jitterMax=$STREAMLIT_JITTER_BUFFER_MAX_FRAMES"
        )

        if (packet.payload.startsWithBytes(byteArrayOf(0x1a.toByte(), 0x45.toByte(), 0xdf.toByte(), 0xa3.toByte()))) {
            Log.w(AUDIO_DIAG_TAG, "streamlitRx BAD_FORMAT WebM/MediaRecorder header detected")
        }

        if (packet.payload.startsWithBytes(byteArrayOf(0x4f.toByte(), 0x67.toByte(), 0x67.toByte(), 0x53.toByte()))) {
            Log.w(AUDIO_DIAG_TAG, "streamlitRx BAD_FORMAT Ogg/Opus header detected")
        }

        if (packet.payload.size > 200) {
            Log.w(AUDIO_DIAG_TAG, "streamlitRx WARN payload too large for normal 20ms raw Opus payloadLen=${packet.payload.size}")
        }

        if (decodedPcm.size != STREAMLIT_FRAME_BYTES) {
            Log.w(AUDIO_DIAG_TAG, "streamlitRx WARN decoded PCM size mismatch pcmLen=${decodedPcm.size} expected=$STREAMLIT_FRAME_BYTES")
        }

        streamlitRxLastLogAtMs = now
        streamlitRxFrameCount = 0
        streamlitRxBadPcmCount = 0
        streamlitRxLargePayloadCount = 0
        streamlitRxPayloadBytes = 0L
    }

    private fun maybeLogTransmitFrame(
        profile: AudioProfile,
        pcmBytes: ByteArray,
        opusBytes: ByteArray,
        bluetoothActive: Boolean,
        audioSource: Int
    ) {
        val now = SystemClock.elapsedRealtime()
        txFrameCount += 1
        txPayloadBytes += opusBytes.size.toLong()

        if (txLastLogAtMs != 0L && now - txLastLogAtMs < AUDIO_DIAG_INTERVAL_MS) return

        val avgPayload = if (txFrameCount > 0) txPayloadBytes / txFrameCount else 0L
        val level = pcmLevelSummary(pcmBytes)

        Log.d(
            AUDIO_DIAG_TAG,
            "tx profile=$profile sampleRate=${profile.sampleRate} pcmLen=${pcmBytes.size} " +
                    "opusLen=${opusBytes.size} avgOpus=$avgPayload frames=$txFrameCount " +
                    "bluetoothActive=$bluetoothActive source=$audioSource $level"
        )

        txLastLogAtMs = now
        txFrameCount = 0
        txPayloadBytes = 0L
    }

    private fun pcmLevelSummary(pcmBytes: ByteArray): String {
        if (pcmBytes.size < 2) return "rms=0 peak=0"

        var i = 0
        var samples = 0
        var sumSquares = 0.0
        var peak = 0

        while (i + 1 < pcmBytes.size) {
            val raw = (pcmBytes[i].toInt() and 0xff) or (pcmBytes[i + 1].toInt() shl 8)
            val sample = raw.toShort().toInt()
            val abs = kotlin.math.abs(sample)
            if (abs > peak) peak = abs
            sumSquares += sample.toDouble() * sample.toDouble()
            samples += 1
            i += 2
        }

        val rms = if (samples > 0) kotlin.math.sqrt(sumSquares / samples).toInt() else 0
        return "rms=$rms peak=$peak"
    }

    private fun startPlaybackLoop() {
        if (isPlaybackRunning) return

        isPlaybackRunning = true

        thread(name = "walkie-playback") {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            var bufferPrimed = false
            var plcFrames = 0

            Log.d(TAG, "playback loop started")

            while (isPlaybackRunning) {
                val queuedFrame: PcmFrame? =
                    synchronized(playbackLock) {
                        if (!bufferPrimed) {
                            val firstSampleRate = playbackQueue.firstOrNull()?.sampleRate ?: STREAMLIT_SAMPLE_RATE
                            val requiredStartFrames = jitterStartFramesFor(firstSampleRate)
                            if (playbackQueue.size < requiredStartFrames) {
                                null
                            } else {
                                bufferPrimed = true
                                plcFrames = 0

                                playbackQueue.removeFirst()
                            }
                        } else {
                            if (playbackQueue.isEmpty()) {
                                null
                            } else {
                                plcFrames = 0

                                playbackQueue.removeFirst()
                            }
                        }
                    }

                val frame =
                    if (queuedFrame != null) {
                        lastPlaybackSampleRate = queuedFrame.sampleRate
                        queuedFrame
                    } else if (bufferPrimed) {
                        val maxPlcFrames = playbackMaxPlcFramesFor(lastPlaybackSampleRate)
                        if (plcFrames < maxPlcFrames) {
                            plcFrames += 1
                            val plcPcm = if (lastPlaybackSampleRate == STREAMLIT_SAMPLE_RATE) {
                                synchronized(streamlitDecoderLock) {
                                    streamlitDecoder?.decodePlcToPcm()
                                }
                            } else {
                                synchronized(appDecoderLock) {
                                    appDecoder?.decodePlcToPcm()
                                }
                            }
                            if (plcPcm != null) PcmFrame(lastPlaybackSampleRate, plcPcm) else null
                        } else {
                            Log.d(TAG, "playback underrun. re-prime after plcFrames=$plcFrames")
                            bufferPrimed = false
                            plcFrames = 0
                            null
                        }
                    } else {
                        null
                    }

                if (frame == null) {
                    try {
                        Thread.sleep(PLAYBACK_IDLE_SLEEP_MS)
                    } catch (_: InterruptedException) {
                    }

                    continue
                }

                playPcm(frame)
            }

            Log.d(TAG, "playback loop stopped")
        }
    }

    private fun playPcm(frame: PcmFrame) {
        try {
            initPlaybackTrack(frame.sampleRate)
            val track = playbackTrack ?: return

            track.write(
                frame.pcmBytes,
                0,
                frame.pcmBytes.size,
                AudioTrack.WRITE_BLOCKING
            )
        } catch (e: Exception) {
            Log.e(TAG, "play pcm failed", e)
        }
    }

    private fun logAudioRouteSnapshot(context: Context, label: String) {
        try {
            val audioManager =
                context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            val outputDevices = audioManager
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .joinToString(separator = ",") { deviceTypeName(it.type) }

            val inputDevices = audioManager
                .getDevices(AudioManager.GET_DEVICES_INPUTS)
                .joinToString(separator = ",") { deviceTypeName(it.type) }

            val communicationDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.communicationDevice?.let { deviceTypeName(it.type) } ?: "null"
            } else {
                "legacy"
            }

            @Suppress("DEPRECATION")
            val legacyScoOn = audioManager.isBluetoothScoOn

            Log.d(
                AUDIO_DIAG_TAG,
                "route label=$label mode=${audioManager.mode} speaker=${audioManager.isSpeakerphoneOn} " +
                        "legacySco=$legacyScoOn btComm=${isBluetoothCommunicationActive(context)} " +
                        "commDevice=$communicationDevice outputs=[$outputDevices] inputs=[$inputDevices]"
            )
        } catch (e: Exception) {
            Log.e(AUDIO_DIAG_TAG, "route snapshot failed label=$label", e)
        }
    }

    private fun deviceTypeName(type: Int): String {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "EARPIECE"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "SPEAKER"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BT_SCO"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BT_A2DP"
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "BUILTIN_MIC"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE_HEADSET"
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE_SPEAKER"
            else -> "TYPE_$type"
        }
    }

    private fun ByteArray.headHex(maxBytes: Int): String {
        return take(maxBytes).joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private fun ByteArray.startsWithBytes(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }

    private fun parsePacket(
        data: ByteArray,
        size: Int
    ): WalkieAudioPacket? {
        if (size < 15) return null

        if (data[0] != MAGIC_1 || data[1] != MAGIC_2) return null
        if (data[2] != TYPE_AUDIO) return null

        val crcIndex = size - 2
        val receivedCrc =
            (data[crcIndex].toInt() and 0xff) or
                    ((data[crcIndex + 1].toInt() and 0xff) shl 8)

        val calculatedCrc = crc16Ccitt(
            data = data,
            offset = 2,
            length = crcIndex - 2
        )

        if (receivedCrc != calculatedCrc) return null

        var index = 3

        val codec = data[index++].toInt() and 0xff
        val channelId = data[index++].toInt() and 0xff
        val targetTypeByte = data[index++].toInt() and 0xff

        val seq =
            (data[index++].toInt() and 0xff) or
                    ((data[index++].toInt() and 0xff) shl 8)

        val senderLen = data[index++].toInt() and 0xff
        if (index + senderLen > crcIndex) return null
        val senderWorkerId = String(data, index, senderLen, Charsets.UTF_8)
        index += senderLen

        val targetWorkerLen = data[index++].toInt() and 0xff
        if (index + targetWorkerLen > crcIndex) return null
        val targetWorkerId = String(data, index, targetWorkerLen, Charsets.UTF_8)
        index += targetWorkerLen

        val targetGroupLen = data[index++].toInt() and 0xff
        if (index + targetGroupLen > crcIndex) return null
        val targetAreaGroup = String(data, index, targetGroupLen, Charsets.UTF_8)
        index += targetGroupLen

        if (index + 2 > crcIndex) return null

        val payloadLen =
            (data[index++].toInt() and 0xff) or
                    ((data[index++].toInt() and 0xff) shl 8)

        if (index + payloadLen != crcIndex) return null

        val payload = ByteArray(payloadLen)
        System.arraycopy(data, index, payload, 0, payloadLen)

        val targetType = when (targetTypeByte) {
            TARGET_USER -> WalkieTargetType.USER
            TARGET_GROUP -> WalkieTargetType.GROUP
            TARGET_ALL -> WalkieTargetType.ALL
            else -> return null
        }

        return WalkieAudioPacket(
            codec = codec,
            channelId = channelId,
            targetType = targetType,
            seq = seq,
            senderWorkerId = senderWorkerId,
            targetWorkerId = targetWorkerId,
            targetAreaGroup = targetAreaGroup,
            payload = payload
        )
    }

    private fun crc16Ccitt(
        data: ByteArray,
        offset: Int,
        length: Int
    ): Int {
        var crc = 0xffff

        for (i in offset until offset + length) {
            crc = crc xor ((data[i].toInt() and 0xff) shl 8)

            for (bit in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) {
                    ((crc shl 1) xor 0x1021) and 0xffff
                } else {
                    (crc shl 1) and 0xffff
                }
            }
        }

        return crc and 0xffff
    }

    private fun getBroadcastAddress(context: Context): InetAddress {
        return try {
            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            val dhcp = wifiManager.dhcpInfo

            if (dhcp == null || dhcp.ipAddress == 0 || dhcp.netmask == 0) {
                return InetAddress.getByName("255.255.255.255")
            }

            val broadcast =
                (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()

            val bytes = ByteArray(4)

            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                bytes[0] = (broadcast and 0xff).toByte()
                bytes[1] = ((broadcast shr 8) and 0xff).toByte()
                bytes[2] = ((broadcast shr 16) and 0xff).toByte()
                bytes[3] = ((broadcast shr 24) and 0xff).toByte()
            } else {
                bytes[3] = (broadcast and 0xff).toByte()
                bytes[2] = ((broadcast shr 8) and 0xff).toByte()
                bytes[1] = ((broadcast shr 16) and 0xff).toByte()
                bytes[0] = ((broadcast shr 24) and 0xff).toByte()
            }

            InetAddress.getByAddress(bytes)
        } catch (_: Exception) {
            InetAddress.getByName("255.255.255.255")
        }
    }

    private fun isBluetoothCommunicationActive(context: Context): Boolean {
        return try {
            val audioManager =
                context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val device = audioManager.communicationDevice
                device?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        device?.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            } else {
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn
            }
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun prepareAudioRouteForTransmit(context: Context): Boolean {
        return try {
            val audioManager =
                context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            if (!hasBluetoothConnectPermission(context)) {
                audioManager.isSpeakerphoneOn = false
                Log.d(TAG, "prepare route: BLUETOOTH_CONNECT missing. speaker fallback disabled, keep speakerphoneOff")
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                routeCommunicationAudioDevice(context)
                waitForBluetoothRouteIfNeeded(context)
                isBluetoothCommunicationActive(context)
            } else {
                waitForLegacyBluetoothScoConnected(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "prepare audio route for transmit failed", e)
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun hasLegacyBluetoothScoDevice(audioManager: AudioManager): Boolean {
        return try {
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            (outputs + inputs).any { device ->
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun waitForLegacyBluetoothScoConnected(
        context: Context,
        timeoutMs: Long = 2800L
    ): Boolean {
        val appCtx = context.applicationContext
        val audioManager = appCtx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (!hasLegacyBluetoothScoDevice(audioManager)) {
            try {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                audioManager.isSpeakerphoneOn = false
            } catch (_: Exception) {
            }
            Log.d(TAG, "legacy bluetooth SCO device not found. speaker fallback disabled, keep speakerphoneOff")
            return false
        }

        val latch = CountDownLatch(1)
        var connected = false

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) return

                val state = intent.getIntExtra(
                    AudioManager.EXTRA_SCO_AUDIO_STATE,
                    AudioManager.SCO_AUDIO_STATE_ERROR
                )

                Log.d(TAG, "legacy bluetooth SCO state=$state")

                if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                    connected = true
                    latch.countDown()
                }
            }
        }

        return try {
            val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appCtx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                appCtx.registerReceiver(receiver, filter)
            }

            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = false
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            Thread.sleep(120L)
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true

            connected = latch.await(timeoutMs, TimeUnit.MILLISECONDS) || audioManager.isBluetoothScoOn

            if (connected) {
                // Give old MIUI devices a short settling window before AudioRecord is opened.
                Thread.sleep(450L)
            }

            Log.d(TAG, "legacy bluetooth SCO wait result connected=$connected isBluetoothScoOn=${audioManager.isBluetoothScoOn}")
            connected
        } catch (e: Exception) {
            Log.e(TAG, "legacy bluetooth SCO wait failed", e)
            false
        } finally {
            try {
                appCtx.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun waitForBluetoothRouteIfNeeded(
        context: Context,
        timeoutMs: Long = 1800L
    ) {
        try {
            val audioManager =
                context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            if (!hasBluetoothConnectPermission(context)) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val hasBluetoothCommunicationDevice =
                    audioManager.availableCommunicationDevices.any { device ->
                        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    }

                if (!hasBluetoothCommunicationDevice) return

                val deadline = SystemClock.elapsedRealtime() + timeoutMs
                while (SystemClock.elapsedRealtime() < deadline) {
                    if (isBluetoothCommunicationActive(context)) {
                        Log.d(TAG, "bluetooth communication route ready")
                        return
                    }
                    Thread.sleep(50L)
                }

                Log.d(TAG, "bluetooth communication route wait timeout")
            } else {
                waitForLegacyBluetoothScoConnected(context, timeoutMs = timeoutMs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "wait for bluetooth route failed", e)
        }
    }

    private fun hasBluetoothConnectPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun registerAudioDeviceCallback(context: Context) {
        if (audioDeviceCallback != null) return

        val audioManager =
            context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        audioDeviceCallback =
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                    if (isTransmitting) routeCommunicationAudioDevice(context) else restoreReceivePlaybackMode(context)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                    if (isTransmitting) routeCommunicationAudioDevice(context) else restoreReceivePlaybackMode(context)
                }
            }

        audioManager.registerAudioDeviceCallback(
            audioDeviceCallback,
            Handler(Looper.getMainLooper())
        )
    }

    private fun unregisterAudioDeviceCallback(context: Context) {
        val callback = audioDeviceCallback ?: return

        try {
            val audioManager =
                context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            audioManager.unregisterAudioDeviceCallback(callback)
        } catch (e: Exception) {
            Log.e(TAG, "unregister audio device callback failed", e)
        } finally {
            audioDeviceCallback = null
        }
    }

    @Suppress("DEPRECATION")
    private fun routeCommunicationAudioDevice(context: Context) {
        try {
            val audioManager =
                context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            if (!hasBluetoothConnectPermission(context)) {
                audioManager.isSpeakerphoneOn = false
                Log.d(TAG, "BLUETOOTH_CONNECT permission missing. speaker output fallback disabled")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val bluetoothDevice =
                    audioManager.availableCommunicationDevices.firstOrNull { device ->
                        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    }

                if (bluetoothDevice != null) {
                    audioManager.isSpeakerphoneOn = false
                    val result = audioManager.setCommunicationDevice(bluetoothDevice)

                    Log.d(
                        TAG,
                        "bluetooth communication device selected result=$result type=${bluetoothDevice.type} name=${bluetoothDevice.productName}"
                    )
                } else {
                    audioManager.clearCommunicationDevice()
                    audioManager.isSpeakerphoneOn = false
                    Log.d(TAG, "no bluetooth communication device. speaker output fallback disabled")
                }
            } else {
                val hasBluetoothSco = hasLegacyBluetoothScoDevice(audioManager)

                if (hasBluetoothSco) {
                    audioManager.isSpeakerphoneOn = false
                    // Do not start SCO from the device callback/start path on Android 10/11.
                    // It is started synchronously inside the transmit audio thread.
                    Log.d(TAG, "legacy bluetooth SCO device found. SCO start deferred until transmit")
                } else {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = false
                    Log.d(TAG, "legacy bluetooth SCO not found. speaker output fallback disabled")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "route communication audio device failed", e)
        }
    }

    private data class WalkieAudioPacket(
        val codec: Int,
        val channelId: Int,
        val targetType: WalkieTargetType,
        val seq: Int,
        val senderWorkerId: String,
        val targetWorkerId: String,
        val targetAreaGroup: String,
        val payload: ByteArray
    )
}
