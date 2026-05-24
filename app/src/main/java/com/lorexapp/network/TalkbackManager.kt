package com.lorexapp.network

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.lorexapp.model.Camera
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.util.concurrent.TimeUnit

/**
 * Streams microphone audio to a Lorex/Dahua camera's two-way audio endpoint.
 *
 * Protocol: HTTP POST to /cgi-bin/audio.cgi with chunked G.711 A-law audio.
 * Sample rate: 8000 Hz, mono — the only format universally accepted by Lorex NVRs.
 *
 * Usage:
 *   val tb = TalkbackManager(camera)
 *   tb.start()   // begin streaming mic → camera
 *   tb.stop()    // stop
 */
class TalkbackManager(private val camera: Camera) {

    companion object {
        private const val SAMPLE_RATE   = 8000
        private const val CHANNEL       = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING      = AudioFormat.ENCODING_PCM_16BIT
    }

    private val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        .coerceAtLeast(1024)

    @Volatile private var running = false
    private var job: Job? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)  // infinite — we stream until stopped
        .readTimeout(0, TimeUnit.SECONDS)
        .authenticator { _, response ->
            val cred = okhttp3.Credentials.basic(camera.username, camera.password)
            if (response.request.header("Authorization") != null) null
            else response.request.newBuilder().header("Authorization", cred).build()
        }
        .build()

    fun start(scope: CoroutineScope) {
        if (running) return
        running = true
        job = scope.launch(Dispatchers.IO) {
            streamAudio()
        }
    }

    fun stop() {
        running = false
        job?.cancel()
        job = null
    }

    val isRunning get() = running

    private fun streamAudio() {
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE, CHANNEL, ENCODING, minBufSize * 4
        )

        try {
            recorder.startRecording()

            val url = "${camera.httpBase()}/cgi-bin/audio.cgi?action=postAudio" +
                      "&channel=${camera.channel - 1}&httptype=singlepart&channel=0"

            val body = object : RequestBody() {
                override fun contentType() =
                    "Audio/G.711Alaw;sampleRate=$SAMPLE_RATE".toMediaType()

                override fun writeTo(sink: BufferedSink) {
                    val pcmBuf = ShortArray(minBufSize)
                    val alawBuf = ByteArray(minBufSize)
                    while (running) {
                        val read = recorder.read(pcmBuf, 0, minBufSize)
                        if (read > 0) {
                            encodeAlaw(pcmBuf, alawBuf, read)
                            sink.write(alawBuf, 0, read)
                            sink.flush()
                        }
                    }
                }
            }

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { /* hold connection open */ }

        } catch (_: Exception) {
            // stream ended or camera disconnected
        } finally {
            recorder.stop()
            recorder.release()
            running = false
        }
    }

    // ── G.711 A-law encoder ───────────────────────────────────────────────────
    // Converts 16-bit PCM samples to 8-bit G.711 A-law bytes.

    private fun encodeAlaw(pcm: ShortArray, out: ByteArray, count: Int) {
        for (i in 0 until count) {
            out[i] = linearToAlaw(pcm[i].toInt())
        }
    }

    private fun linearToAlaw(pcm: Int): Byte {
        var sample = pcm
        val sign: Int
        val exponent: Int
        val mantissa: Int
        val alaw: Int

        sign = if (sample >= 0) 0xD5 else 0x55
        if (sample < 0) sample = -sample
        if (sample > 32767) sample = 32767

        exponent = when {
            sample < 256   -> 0
            sample < 512   -> 1
            sample < 1024  -> 2
            sample < 2048  -> 3
            sample < 4096  -> 4
            sample < 8192  -> 5
            sample < 16384 -> 6
            else           -> 7
        }

        mantissa = if (exponent == 0) {
            (sample shr 1) and 0x0F
        } else {
            (sample shr (exponent)) and 0x0F
        }

        alaw = (sign xor ((exponent shl 4) or mantissa)) and 0xFF
        return alaw.toByte()
    }
}
