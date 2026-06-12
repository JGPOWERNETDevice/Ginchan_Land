package net.jgpower.gichan_land.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteOrder
import java.util.ArrayDeque
import kotlin.concurrent.thread
import kotlin.math.max
import net.jgpower.gichan_land.data.walkie.WalkieTarget
import net.jgpower.gichan_land.data.walkie.WalkieTargetType
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
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

    private const val SAMPLE_RATE = 8000
    private const val FRAME_DURATION_MS = 20
    private const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_DURATION_MS / 1000
    private const val FRAME_BYTES = FRAME_SAMPLES * 2
    private const val OPUS_BITRATE = 16000

    private const val MAX_PAYLOAD_BYTES = 1024

    private const val JITTER_BUFFER_START_FRAMES = 2
    private const val JITTER_BUFFER_MAX_FRAMES = 8
    private const val PLAYBACK_IDLE_SLEEP_MS = 4L

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
    private var opusDecoder: OpusCodec? = null

    @Volatile
    private var audioDeviceCallback: AudioDeviceCallback? = null

    @Volatile
    private var playbackTrack: AudioTrack? = null

    private val playbackLock = Object()
    private val playbackQueue = ArrayDeque<ByteArray>()

    private var sequence = 0

    fun start(
        context: Context,
        workerId: String,
        areaGroup: String
    ) {
        appContext = context.applicationContext

        currentWorkerId = workerId
        currentAreaGroup = areaGroup
        broadcastAddress = getBroadcastAddress(context)

        opusDecoder = OpusCodec(
            sampleRate = SAMPLE_RATE,
            channels = 1,
            frameBytes = FRAME_BYTES,
            bitrate = OPUS_BITRATE
        )

        enterCommunicationMode(context)
        enterCommunicationMode(context)
        registerAudioDeviceCallback(context)
        routeCommunicationAudioDevice(context)
        initPlaybackTrack()
        startPlaybackLoop()

        if (isReceiverRunning && socket != null) {
            Log.d(TAG, "already started. workerId=$workerId areaGroup=$areaGroup")
            return
        }

        val newSocket = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            bind(InetSocketAddress(UDP_PORT))
        }

        socket = newSocket

        startReceiver(newSocket)

        Log.d(
            TAG,
            "started workerId=$workerId areaGroup=$areaGroup sampleRate=$SAMPLE_RATE frameBytes=$FRAME_BYTES"
        )
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
        opusDecoder = null

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
        }

        appContext?.let {
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

    @Suppress("DEPRECATION")
    private fun enterCommunicationMode(context: Context) {
        try {
            val audioManager =
                context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true

            Log.d(TAG, "audio mode set MODE_IN_COMMUNICATION speakerOn=true")
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

            Log.d(TAG, "audio mode restored MODE_NORMAL speakerOn=false")
        } catch (e: Exception) {
            Log.e(TAG, "exit communication mode failed", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startTransmit(context: Context): Boolean {
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

        if (isTransmitting) {
            return true
        }

        isTransmitting = true

        thread(name = "walkie-transmit") {
            var audioRecord: AudioRecord? = null
            var encoder: OpusCodec? = null
            var echoCanceler: AcousticEchoCanceler? = null

            try {
                val minBuffer = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    max(minBuffer, FRAME_BYTES * 4)
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord init failed")
                    isTransmitting = false
                    return@thread
                }

                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(audioRecord.audioSessionId)
                    echoCanceler?.enabled = true
                    Log.d(TAG, "AcousticEchoCanceler enabled=${echoCanceler?.enabled}")
                } else {
                    Log.d(TAG, "AcousticEchoCanceler not available")
                }

                encoder = OpusCodec(
                    sampleRate = SAMPLE_RATE,
                    channels = 1,
                    frameBytes = FRAME_BYTES,
                    bitrate = OPUS_BITRATE
                )

                val pcmFrame = ByteArray(FRAME_BYTES)

                audioRecord.startRecording()

                Log.d(TAG, "transmit started")

                while (isTransmitting) {
                    val readOk = readFullFrame(
                        audioRecord = audioRecord,
                        buffer = pcmFrame
                    )

                    if (!readOk) {
                        continue
                    }

                    val opusBytes = encoder.encodePcm(pcmFrame)

                    sendAudioPacket(
                        socket = udpSocket,
                        senderWorkerId = workerId,
                        target = target,
                        opusBytes = opusBytes
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

    private fun sendAudioPacket(
        socket: DatagramSocket,
        senderWorkerId: String,
        target: WalkieTarget,
        opusBytes: ByteArray
    ) {
        try {
            if (opusBytes.isEmpty() || opusBytes.size > MAX_PAYLOAD_BYTES) {
                return
            }

            val packetBytes = buildPacket(
                senderWorkerId = senderWorkerId,
                target = target,
                payload = opusBytes
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
        } catch (e: Exception) {
            Log.e(TAG, "send failed", e)
        }
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

            val decodedPcm =
                opusDecoder?.decodeToPcm(packet.payload) ?: return

            if (decodedPcm.isEmpty()) return

            enqueuePlayback(decodedPcm)
        } catch (e: Exception) {
            Log.e(TAG, "handle packet failed", e)
        }
    }

    private fun initPlaybackTrack() {
        synchronized(playbackLock) {
            if (playbackTrack != null) {
                return
            }

            try {
                val minBuffer = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
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
                    .setBufferSizeInBytes(max(minBuffer, FRAME_BYTES * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.setVolume(AudioTrack.getMaxVolume())
                track.play()

                playbackTrack = track

                Log.d(TAG, "playback track started USAGE_VOICE_COMMUNICATION")
            } catch (e: Exception) {
                playbackTrack = null
                Log.e(TAG, "playback track init failed", e)
            }
        }
    }

    private fun enqueuePlayback(pcmBytes: ByteArray) {
        synchronized(playbackLock) {
            while (playbackQueue.size >= JITTER_BUFFER_MAX_FRAMES) {
                playbackQueue.removeFirst()
            }

            playbackQueue.addLast(pcmBytes)
            playbackLock.notifyAll()
        }
    }

    private fun startPlaybackLoop() {
        if (isPlaybackRunning) return

        isPlaybackRunning = true

        thread(name = "walkie-playback") {
            var bufferPrimed = false

            Log.d(TAG, "playback loop started")

            while (isPlaybackRunning) {
                val frame: ByteArray? =
                    synchronized(playbackLock) {
                        if (!bufferPrimed) {
                            if (playbackQueue.size < JITTER_BUFFER_START_FRAMES) {
                                null
                            } else {
                                bufferPrimed = true
                                playbackQueue.removeFirst()
                            }
                        } else {
                            if (playbackQueue.isEmpty()) {
                                bufferPrimed = false
                                null
                            } else {
                                playbackQueue.removeFirst()
                            }
                        }
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

    private fun playPcm(pcmBytes: ByteArray) {
        try {
            val track = playbackTrack ?: return

            track.write(
                pcmBytes,
                0,
                pcmBytes.size,
                AudioTrack.WRITE_BLOCKING
            )
        } catch (e: Exception) {
            Log.e(TAG, "play pcm failed", e)
        }
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
                    routeCommunicationAudioDevice(context)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                    routeCommunicationAudioDevice(context)
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
                audioManager.isSpeakerphoneOn = true
                Log.d(TAG, "BLUETOOTH_CONNECT permission missing. speaker output used")
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
                    audioManager.isSpeakerphoneOn = true

                    Log.d(TAG, "no bluetooth communication device. speaker output used")
                }
            } else {
                val hasBluetoothSco =
                    audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
                        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    }

                if (hasBluetoothSco) {
                    audioManager.isSpeakerphoneOn = false
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true

                    Log.d(TAG, "legacy bluetooth SCO started")
                } else {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = true

                    Log.d(TAG, "legacy bluetooth SCO not found. speaker output used")
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
