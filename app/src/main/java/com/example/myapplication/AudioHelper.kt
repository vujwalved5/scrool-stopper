package com.example.myapplication

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log

class AudioHelper {
    companion object {
        private var mediaPlayer: MediaPlayer? = null
        private const val TAG = "AudioHelper"

        fun play(context: Context) {
            if (isPlaying()) {
                Log.d(TAG, "Already playing, skipping.")
                return
            }

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            // Maximize volume
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)

            val audioFocusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .build()
            } else {
                null
            }

            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.requestAudioFocus(it) } ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mediaPlayer = MediaPlayer.create(context, R.raw.message).apply {
                    setOnCompletionListener {
                        stop()
                        // Abandon focus
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            audioFocusRequest?.let { req -> audioManager.abandonAudioFocusRequest(req) }
                        } else {
                            @Suppress("DEPRECATION")
                            audioManager.abandonAudioFocus(null)
                        }
                    }
                    start()
                }
            } else {
                Log.e(TAG, "Failed to get audio focus")
            }
        }

        fun stop() {
            try {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        mp.stop()
                    }
                    mp.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping MediaPlayer", e)
            } finally {
                mediaPlayer = null
            }
        }

        fun stopAll() {
            stop()
        }

        fun isPlaying(): Boolean {
            return try {
                mediaPlayer?.isPlaying == true
            } catch (e: Exception) {
                false
            }
        }
    }
}
