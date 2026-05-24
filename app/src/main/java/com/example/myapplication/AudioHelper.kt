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
            Log.d(TAG, "play() requested")
            if (isPlaying()) {
                Log.d(TAG, "Already playing, stopping first.")
                stop()
            }

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            // Log current volume levels
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            Log.d(TAG, "Current volume: $currentVolume / $maxVolume")

            // Maximize volume for the test/interrupt
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
                Log.d(TAG, "Volume set to max: $maxVolume")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set volume", e)
            }

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
                Log.d(TAG, "Audio focus granted")
                try {
                    mediaPlayer = MediaPlayer.create(context, R.raw.message).apply {
                        if (this == null) {
                            Log.e(TAG, "MediaPlayer.create returned null! Check if R.raw.message exists and is valid.")
                            return
                        }
                        
                        setOnPreparedListener {
                            Log.d(TAG, "MediaPlayer prepared, starting playback")
                            it.start()
                        }

                        setOnCompletionListener {
                            Log.d(TAG, "Playback completed")
                            stop()
                            // Abandon focus
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                audioFocusRequest?.let { req -> audioManager.abandonAudioFocusRequest(req) }
                            } else {
                                @Suppress("DEPRECATION")
                                audioManager.abandonAudioFocus(null)
                            }
                        }

                        setOnErrorListener { mp, what, extra ->
                            Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                            stop()
                            true
                        }

                        // MediaPlayer.create calls prepare() internally, but let's be explicit if we weren't using .create
                        // start() is also called if it's already prepared, but we added a listener just in case.
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during MediaPlayer creation/setup", e)
                }
            } else {
                Log.e(TAG, "Failed to get audio focus. Result code: $result")
            }
        }

        fun stop() {
            Log.d(TAG, "stop() called")
            try {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        mp.stop()
                    }
                    mp.release()
                    Log.d(TAG, "MediaPlayer released")
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
                Log.e(TAG, "Error checking isPlaying", e)
                false
            }
        }
    }
}
