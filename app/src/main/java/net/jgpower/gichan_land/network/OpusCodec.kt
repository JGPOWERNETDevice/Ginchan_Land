package net.jgpower.gichan_land.network

import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusDecoder
import io.github.jaredmdobson.concentus.OpusEncoder

class OpusCodec(
    private val sampleRate: Int = 8000,
    private val channels: Int = 1,
    private val frameBytes: Int = 320,
    bitrate: Int = 16000
) {
    private val frameSamples = frameBytes / 2

    private val encoder = OpusEncoder(
        sampleRate,
        channels,
        OpusApplication.OPUS_APPLICATION_VOIP
    ).apply {
        this.bitrate = bitrate
    }

    private val decoder = OpusDecoder(
        sampleRate,
        channels
    )

    fun encodePcm(pcmBytes: ByteArray): ByteArray {
        require(pcmBytes.size == frameBytes) {
            "PCM frame size must be $frameBytes bytes"
        }

        val pcmShorts = ShortArray(frameSamples)

        var byteIndex = 0
        for (i in 0 until frameSamples) {
            val low = pcmBytes[byteIndex].toInt() and 0xff
            val high = pcmBytes[byteIndex + 1].toInt()
            pcmShorts[i] = ((high shl 8) or low).toShort()
            byteIndex += 2
        }

        val encodedBuffer = ByteArray(400)

        val encodedBytes = encoder.encode(
            pcmShorts,
            0,
            frameSamples,
            encodedBuffer,
            0,
            encodedBuffer.size
        )

        return encodedBuffer.copyOf(encodedBytes)
    }

    fun decodeToPcm(opusBytes: ByteArray): ByteArray {
        val decodedShorts = ShortArray(frameSamples)

        val decodedSamples = decoder.decode(
            opusBytes,
            0,
            opusBytes.size,
            decodedShorts,
            0,
            frameSamples,
            false
        )

        val pcmBytes = ByteArray(decodedSamples * 2)

        var byteIndex = 0
        for (i in 0 until decodedSamples) {
            val sample = decodedShorts[i].toInt()

            pcmBytes[byteIndex] = (sample and 0xff).toByte()
            pcmBytes[byteIndex + 1] = ((sample shr 8) and 0xff).toByte()

            byteIndex += 2
        }

        return pcmBytes
    }
}