package com.example.aura.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlin.math.min

private const val TAG = "VoiceMessageManager"
private const val SAMPLE_RATE = 8000
private const val MAX_SECONDS = 3
private const val MAX_SAMPLES = SAMPLE_RATE * MAX_SECONDS

/**
 * Захват PCM 8 kHz mono через [AudioRecord], кодирование через [Codec2Bridge],
 * лимит длительности [MAX_SECONDS] с.
 */
class VoiceMessageManager(private val context: Context) {

    @Volatile
    private var record: AudioRecord? = null

    @Volatile
    private var recording = false
    @Volatile
    private var captureThread: Thread? = null

    private val buffer = ShortArray(MAX_SAMPLES)
    @Volatile
    private var sampleCount = 0
    private var audioManagerRef: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { _ ->
        // Для записи достаточно удерживать фокус; реакция на изменения не требуется.
    }

    private fun requestAudioFocusForRecording(): Boolean {
        return try {
            val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManagerRef = am
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(true)
                    .build()
                audioFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                )
            }
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (t: Throwable) {
            Log.e(TAG, "requestAudioFocusForRecording", t)
            false
        }
    }

    private fun abandonAudioFocusForRecording() {
        val am = audioManagerRef ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(focusChangeListener)
            }
        } catch (_: Exception) {
        } finally {
            audioFocusRequest = null
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (recording) return false
        if (!requestAudioFocusForRecording()) {
            Log.w(TAG, "Audio focus denied for recording")
            return false
        }
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) {
            Log.e(TAG, "getMinBufferSize failed: $minBuf")
            abandonAudioFocusForRecording()
            return false
        }
        try {
            val ar = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2,
            )
            if (ar.state != AudioRecord.STATE_INITIALIZED) {
                ar.release()
                Log.e(TAG, "AudioRecord not initialized")
                abandonAudioFocusForRecording()
                return false
            }
            sampleCount = 0
            record = ar
            ar.startRecording()
            recording = true
            val t = Thread({
                try {
                    val tmp = ShortArray(minBuf / 2)
                    while (recording && record === ar) {
                        val r = try {
                            ar.read(tmp, 0, tmp.size)
                        } catch (readErr: Throwable) {
                            Log.w(TAG, "AudioRecord.read failed", readErr)
                            break
                        }
                        if (r <= 0) continue
                        val can = min(r, MAX_SAMPLES - sampleCount)
                        if (can > 0) {
                            System.arraycopy(tmp, 0, buffer, sampleCount, can)
                            sampleCount += can
                        }
                        if (sampleCount >= MAX_SAMPLES) {
                            recording = false
                            break
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "captureThread crashed", t)
                } finally {
                    if (captureThread === Thread.currentThread()) {
                        captureThread = null
                    }
                }
            }, "AuraVoiceCap")
            captureThread = t
            t.start()
            return true
        } catch (t: Throwable) {
            Log.e(TAG, "startRecording", t)
            record = null
            recording = false
            abandonAudioFocusForRecording()
            return false
        }
    }

    /** Остановить запись; вернуть сжатые байты (Codec2 JNI) или пусто. */
    fun stopRecordingAndEncode(): Pair<ByteArray, Long> {
        val ar = stopAndReleaseRecord()
        if (ar == null) return ByteArray(0) to 0L
        val n = sampleCount.coerceAtLeast(0)
        if (n == 0) return ByteArray(0) to 0L
        val pcm = buffer.copyOf(n)
        val enc = try {
            Codec2Bridge.encodePcm8kMono(pcm)
        } catch (t: Throwable) {
            Log.e(TAG, "encodePcm8kMono", t)
            ByteArray(0)
        }
        val durMs = (n * 1000L) / SAMPLE_RATE
        return enc to durMs
    }

    fun cancelRecording() {
        stopAndReleaseRecord()
        sampleCount = 0
    }

    private fun stopAndReleaseRecord(): AudioRecord? {
        recording = false
        val ar = record
        record = null
        if (ar != null) {
            try {
                ar.stop()
            } catch (_: Throwable) {
            }
        }
        val t = captureThread
        if (t != null && t !== Thread.currentThread()) {
            try {
                t.join(250)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        captureThread = null
        if (ar != null) {
            try {
                ar.release()
            } catch (_: Throwable) {
            }
        }
        abandonAudioFocusForRecording()
        return ar
    }
}
