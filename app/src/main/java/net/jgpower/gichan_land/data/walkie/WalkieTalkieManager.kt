package net.jgpower.gichan_land.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import net.jgpower.gichan_land.data.walkie.WalkieTarget
import net.jgpower.gichan_land.data.walkie.WalkieTargetType
import org.json.JSONObject

object WalkieTalkieManager {

    private const val TAG = "WALKIE"
    private const val UDP_PORT = 51515
    private const val CHANNEL_ID = 1

    private const val SAMPLE_RATE = 16000
    private const val FRAME_SAMPLES = 320
    private const val FRAME_BYTES = FRAME_SAMPLES * 2

    private const val PLAYBACK_GAIN = 2.2f
    private const val INPUT_GAIN = 1.6f
    private const val NOISE_GATE_THRESHOLD = 450
    private const val MIXER_IDLE_SLEEP_MS = 10L

    @Volatile
    private var socket: DatagramSocket? = null

    @Volatile
    private var isReceiverRunning = false

    @Volatile
    private var isMixerRunning = false

    @Volatile
    private var isTransmitting = false

    @Volatile
    private var currentWorkerId: String? = null

    @Volatile
    private var currentAreaGroup: String? = null

    @Volatile
    private var currentTarget: WalkieTarget? = null

    private var sequence = 0

    private val senderQueues =
        ConcurrentHashMap<String, ArrayDeque<ShortArray>>()

    fun start(
        context: Context,
        workerId: String,
        areaGroup: String
    ) {
        currentWorkerId = workerId
        currentAreaGroup = areaGroup

        if (isReceiverRunning && socket != null) {
            Log.d(TAG, "walkie already started. update workerId=$workerId areaGroup=$areaGroup")
            return
        }

        val newSocket = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            bind(InetSocketAddress(UDP_PORT))
        }

        socket = newSocket

        startReceiver(newSocket)
        startMixer()

        Log.d(TAG, "walkie started workerId=$workerId areaGroup=$areaGroup")
    }

    fun stop() {
        stopTransmit()

        isReceiverRunning = false
        isMixerRunning = false

        try {
            socket?.close()
        } catch (_: Exception) {
        }

        socket = null
        senderQueues.clear()

        Log.d(TAG, "walkie stopped")
    }

    fun setTarget(target: WalkieTarget?) {
        currentTarget = target
        Log.d(TAG, "target changed=$target")
    }

    fun isTransmitRunning(): Boolean {
        return isTransmitting
    }

    fun isStarted(): Boolean {
        return isReceiverRunning && socket != null
    }

    @SuppressLint("MissingPermission")
    fun startTransmit(context: Context): Boolean {
        val workerId = currentWorkerId
        val target = currentTarget
        val udpSocket = socket

        if (workerId.isNullOrBlank() || target == null || udpSocket == null) {
            Log.d(TAG, "startTransmit failed. workerId=$workerId target=$target socket=$udpSocket")
            return false
        }

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "startTransmit failed. RECORD_AUDIO denied")
            return false
        }

        if (isTransmitting) {
            return true
        }

        isTransmitting = true

        thread(name = "walkie-transmit") {
            var audioRecord: AudioRecord? = null

            try {
                val minBuffer = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    max(minBuffer, FRAME_BYTES * 8)
                )

                val buffer = ByteArray(FRAME_BYTES)

                audioRecord.startRecording()

                Log.d(TAG, "transmit started")

                while (isTransmitting) {
                    val read = audioRecord.read(buffer, 0, buffer.size)

                    if (read > 0) {
                        val audioBytes =
                            if (read == buffer.size) {
                                buffer.copyOf()
                            } else {
                                buffer.copyOf(read)
                            }

                        val processedBytes = processInputFrame(audioBytes)

                        sendAudioPacket(
                            socket = udpSocket,
                            senderWorkerId = workerId,
                            target = target,
                            audioBytes = processedBytes
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "transmit failed", e)
            } finally {
                try {
                    audioRecord?.stop()
                } catch (_: Exception) {
                }

                audioRecord?.release()
                Log.d(TAG, "transmit stopped")
            }
        }

        return true
    }

    fun stopTransmit() {
        isTransmitting = false
    }

    private fun sendAudioPacket(
        socket: DatagramSocket,
        senderWorkerId: String,
        target: WalkieTarget,
        audioBytes: ByteArray
    ) {
        try {
            val json = JSONObject().apply {
                put("channelId", CHANNEL_ID)
                put("senderWorkerId", senderWorkerId)
                put("targetType", target.targetType.name)
                put("targetWorkerId", target.targetWorkerId)
                put("targetAreaGroup", target.targetAreaGroup)
                put("seq", sequence++)
                put("timestamp", System.currentTimeMillis())
                put(
                    "audio",
                    Base64.encodeToString(audioBytes, Base64.NO_WRAP)
                )
            }

            val bytes = json.toString().toByteArray(Charsets.UTF_8)

            val packet = DatagramPacket(
                bytes,
                bytes.size,
                InetAddress.getByName("255.255.255.255"),
                UDP_PORT
            )

            socket.send(packet)
        } catch (e: Exception) {
            Log.e(TAG, "send packet failed", e)
        }
    }

    private fun startReceiver(socket: DatagramSocket) {
        isReceiverRunning = true

        thread(name = "walkie-receiver") {
            val buffer = ByteArray(4096)

            Log.d(TAG, "receiver started")

            while (isReceiverRunning) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    val text = String(
                        packet.data,
                        packet.offset,
                        packet.length,
                        Charsets.UTF_8
                    )

                    handleIncomingPacket(text)
                } catch (e: Exception) {
                    if (isReceiverRunning) {
                        Log.e(TAG, "receive failed", e)
                    }
                }
            }

            Log.d(TAG, "receiver stopped")
        }
    }

    private fun handleIncomingPacket(text: String) {
        try {
            val json = JSONObject(text)

            val channelId = json.optInt("channelId")
            if (channelId != CHANNEL_ID) return

            val senderWorkerId = json.optString("senderWorkerId")
            val myWorkerId = currentWorkerId ?: return

            if (senderWorkerId.isBlank()) return
            if (senderWorkerId == myWorkerId) return

            if (!isPacketForMe(json)) return

            val audioBase64 = json.optString("audio")
            if (audioBase64.isBlank()) return

            val audioBytes = Base64.decode(audioBase64, Base64.NO_WRAP)
            val samples = bytesToShorts(audioBytes)

            if (samples.isEmpty()) return

            val queue = senderQueues.getOrPut(senderWorkerId) {
                ArrayDeque()
            }

            synchronized(queue) {
                while (queue.size > 6) {
                    queue.removeFirst()
                }

                queue.addLast(samples)
            }
        } catch (e: Exception) {
            Log.e(TAG, "handle packet failed", e)
        }
    }

    private fun isPacketForMe(json: JSONObject): Boolean {
        val myWorkerId = currentWorkerId ?: return false
        val myAreaGroup = currentAreaGroup ?: return false

        val targetType = json.optString("targetType")
        val targetWorkerId = json.optString("targetWorkerId")
        val targetAreaGroup = json.optString("targetAreaGroup")

        return when (targetType) {
            WalkieTargetType.USER.name -> {
                targetWorkerId == myWorkerId
            }

            WalkieTargetType.GROUP.name -> {
                targetAreaGroup == myAreaGroup
            }

            WalkieTargetType.ALL.name -> {
                true
            }

            else -> false
        }
    }

    private fun startMixer() {
        if (isMixerRunning) return

        isMixerRunning = true

        thread(name = "walkie-mixer") {
            var audioTrack: AudioTrack? = null

            try {
                val minBuffer = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build()
                    )
                    .setBufferSizeInBytes(max(minBuffer, FRAME_BYTES * 10))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack.setVolume(AudioTrack.getMaxVolume())
                audioTrack.play()

                Log.d(TAG, "mixer started")

                while (isMixerRunning) {
                    val mixed = ShortArray(FRAME_SAMPLES)
                    var hasAudio = false

                    senderQueues.forEach { (_, queue) ->
                        val frame: ShortArray? = synchronized(queue) {
                            if (queue.isEmpty()) null else queue.removeFirst()
                        }

                        if (frame != null) {
                            hasAudio = true
                            val count = min(FRAME_SAMPLES, frame.size)

                            for (i in 0 until count) {
                                val sum = mixed[i].toInt() + (frame[i].toInt() / 2)
                                mixed[i] = sum.coerceIn(
                                    Short.MIN_VALUE.toInt(),
                                    Short.MAX_VALUE.toInt()
                                ).toShort()
                            }
                        }
                    }

                    if (hasAudio) {
                        applySoftGain(mixed)

                        audioTrack.write(
                            mixed,
                            0,
                            mixed.size,
                            AudioTrack.WRITE_BLOCKING
                        )
                    } else {
                        Thread.sleep(MIXER_IDLE_SLEEP_MS)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "mixer failed", e)
            } finally {
                try {
                    audioTrack?.stop()
                } catch (_: Exception) {
                }

                audioTrack?.release()
                Log.d(TAG, "mixer stopped")
            }
        }
    }

    private fun processInputFrame(bytes: ByteArray): ByteArray {
        val samples = bytesToShorts(bytes)
        if (samples.isEmpty()) return bytes

        var peak = 0
        for (sample in samples) {
            peak = max(peak, abs(sample.toInt()))
        }

        // 너무 작은 소리는 배경 노이즈로 판단하고 무음 처리
        if (peak < NOISE_GATE_THRESHOLD) {
            return ByteArray(bytes.size)
        }

        for (i in samples.indices) {
            val value = (samples[i] * INPUT_GAIN).toInt()
            samples[i] = softLimit(value)
        }

        return shortsToBytes(samples)
    }

    private fun softLimit(value: Int): Short {
        val normalized = value / 32768.0f
        val limited = normalized.coerceIn(-1.0f, 1.0f)

        val softened =
            limited / (1.0f + abs(limited) * 0.25f)

        return (softened * 32767).toInt()
            .coerceIn(
                Short.MIN_VALUE.toInt(),
                Short.MAX_VALUE.toInt()
            )
            .toShort()
    }

    private fun applySoftGain(samples: ShortArray) {
        for (i in samples.indices) {
            val value = (samples[i] * PLAYBACK_GAIN).toInt()
            samples[i] = softLimit(value)
        }
    }

    private fun bytesToShorts(bytes: ByteArray): ShortArray {
        val shortCount = bytes.size / 2
        val result = ShortArray(shortCount)

        for (i in 0 until shortCount) {
            val low = bytes[i * 2].toInt() and 0xff
            val high = bytes[i * 2 + 1].toInt()
            result[i] = ((high shl 8) or low).toShort()
        }

        return result
    }

    private fun shortsToBytes(samples: ShortArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)

        for (i in samples.indices) {
            val value = samples[i].toInt()

            bytes[i * 2] = (value and 0xff).toByte()
            bytes[i * 2 + 1] = ((value shr 8) and 0xff).toByte()
        }

        return bytes
    }
}