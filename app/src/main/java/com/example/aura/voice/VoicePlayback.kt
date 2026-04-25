package com.example.aura.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "VoicePlayback"
private const val SAMPLE_RATE = 8000

/** Воспроизведение PCM 8 kHz mono через [AudioTrack]. */
object VoicePlayback {

    private val current = AtomicReference<AudioTrack?>(null)
    private val playbackEnd = AtomicReference<(() -> Unit)?>(null)
    private val main = Handler(Looper.getMainLooper())
    private var pendingComplete: Runnable? = null
    private var audioManagerRef: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    @Volatile
    private var hasAudioFocus = false
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> {
                // При потере фокуса (например звонок) сразу останавливаем/ставим на паузу воспроизведение.
                main.post { stop() }
            }
        }
    }

    private fun findBuiltinSpeaker(am: AudioManager): AudioDeviceInfo? =
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { dev ->
            when (dev.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> true
                else ->
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        dev.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE
            }
        }

    private fun requestAudioFocus(context: Context): Boolean {
        val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManagerRef = am
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusListener, main)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(true)
                .build()
            audioFocusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            )
        }
        hasAudioFocus = granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        val am = audioManagerRef ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(focusListener)
            }
        } catch (_: Exception) {
        } finally {
            hasAudioFocus = false
            audioFocusRequest = null
        }
    }

    fun stop() {
        pendingComplete?.let { main.removeCallbacks(it) }
        pendingComplete = null
        val t = current.getAndSet(null)
        if (t != null) {
            try {
                t.stop()
            } catch (_: Exception) {
            }
            t.release()
        }
        abandonAudioFocus()
        playbackEnd.getAndSet(null)?.invoke()
    }

    fun playPcm8kMono(context: Context, pcm: ShortArray, onComplete: () -> Unit = {}) {
        stop()
        if (pcm.isEmpty()) {
            main.post { onComplete() }
            return
        }
        playbackEnd.set(onComplete)
        val appCtx = context.applicationContext
        val am = appCtx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val speaker = findBuiltinSpeaker(am)
        val bytes = ByteArray(pcm.size * 2)
        var bi = 0
        for (s in pcm) {
            bytes[bi++] = (s.toInt() and 0xFF).toByte()
            bytes[bi++] = ((s.toInt() shr 8) and 0xFF).toByte()
        }
        val attr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val fmt = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(bytes.size)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(attr)
                .setAudioFormat(fmt)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(bytes.size.coerceAtLeast(minBuf))
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack", e)
            playbackEnd.getAndSet(null)?.invoke()
            return
        }
        if (speaker != null) {
            try {
                track.setPreferredDevice(speaker)
            } catch (e: Exception) {
                Log.w(TAG, "setPreferredDevice(speaker)", e)
            }
        }
        current.set(track)
        if (!requestAudioFocus(appCtx)) {
            Log.w(TAG, "Audio focus denied")
            current.set(null)
            track.release()
            playbackEnd.getAndSet(null)?.invoke()
            return
        }
        var written = 0
        while (written < bytes.size) {
            val w = track.write(bytes, written, bytes.size - written)
            if (w < 0) break
            written += w
        }
        track.play()
        val durMs = (pcm.size * 1000L) / SAMPLE_RATE + 80L
        val r = Runnable {
            pendingComplete = null
            val t2 = current.getAndSet(null)
            if (t2 != null) {
                try {
                    t2.stop()
                } catch (_: Exception) {
                }
                t2.release()
            }
            abandonAudioFocus()
            playbackEnd.getAndSet(null)?.invoke()
        }
        pendingComplete = r
        main.postDelayed(r, durMs)
    }

    fun playEncodedCodec2(context: Context, encoded: ByteArray, onComplete: () -> Unit = {}) {
        val pcm = Codec2Bridge.decodeToPcm8kMono(encoded)
        playPcm8kMono(context, pcm, onComplete)
    }
}
