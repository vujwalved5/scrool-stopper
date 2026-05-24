package com.example.myapplication

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ShortsAccessibilityService : AccessibilityService() {

    private val TAG = "SHORTS_DEBUG"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "ShortsMonitoringChannel"

    private var isOnShorts = false
    private var shortsStartTime = 0L
    private var hasPlayedOnce = false
    private var shortsAbsentCount = 0

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var initialTimerJob: Job? = null
    private var repeatTimerJob: Job? = null

    private lateinit var prefs: SharedPreferences

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                Log.d(TAG, "Screen off detected, resetting timers and stopping audio.")
                resetAllTimers()
                AudioHelper.stop()
            }
        }
    }

    private val debugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.shortsinterrupter.DEBUG_TRIGGER" -> {
                    Log.d(TAG, "DEBUG_TRIGGER received")
                    triggerInterrupt()
                }
                "com.shortsinterrupter.DEBUG_SKIP_TIMER" -> {
                    Log.d(TAG, "DEBUG_SKIP_TIMER received")
                    initialTimerJob?.cancel()
                    triggerInterrupt()
                    startRepeatTimer()
                }
                "com.shortsinterrupter.DEBUG_SKIP_REPEAT" -> {
                    Log.d(TAG, "DEBUG_SKIP_REPEAT received")
                    repeatTimerJob?.cancel()
                    triggerInterrupt()
                    startRepeatTimer()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        startForegroundService()
        prefs = getSharedPreferences("shorts_stats", Context.MODE_PRIVATE)
        
        val screenFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        val debugFilter = IntentFilter().apply {
            addAction("com.shortsinterrupter.DEBUG_TRIGGER")
            addAction("com.shortsinterrupter.DEBUG_SKIP_TIMER")
            addAction("com.shortsinterrupter.DEBUG_SKIP_REPEAT")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOffReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(debugReceiver, debugFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(screenOffReceiver, screenFilter)
            registerReceiver(debugReceiver, debugFilter)
        }
    }

    private fun startForegroundService() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Shorts Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoring YouTube Shorts activity"
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Shorts Monitoring Active")
            .setContentText("Watching for Shorts activity...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // BUG FIX 1: Detect leaving YouTube app
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.packageName != "com.google.android.youtube") {
                if (isOnShorts) {
                    Log.d(TAG, "User left YouTube. Immediate reset.")
                    resetAllTimers()
                    AudioHelper.stop()
                }
                return
            }
        }

        if (event.packageName != "com.google.android.youtube") return

        val rootNode = rootInActiveWindow ?: return
        val nodeTextList = mutableListOf<String>()
        traverseNodeTree(rootNode, nodeTextList)

        val shortsDetected = detectShorts(nodeTextList)

        if (shortsDetected) {
            shortsAbsentCount = 0
            if (!isOnShorts) {
                Log.d(TAG, "Entered Shorts. Starting 3-minute timer.")
                isOnShorts = true
                shortsStartTime = SystemClock.elapsedRealtime()
                incrementStat("shorts_open_count")
                startInitialTimer()
            }
        } else {
            // BUG FIX 1: Debounce internal navigation away from Shorts
            if (isOnShorts) {
                shortsAbsentCount++
                if (shortsAbsentCount >= 3) {
                    Log.d(TAG, "Exited Shorts (debounced). Resetting everything.")
                    recordSessionDuration()
                    resetAllTimers()
                }
            }
        }
    }

    private fun startInitialTimer() {
        initialTimerJob?.cancel()
        initialTimerJob = serviceScope.launch {
            delay(3 * 60 * 1000) // 3 minutes
            
            if (isOnShorts && (SystemClock.elapsedRealtime() - shortsStartTime >= 180000)) {
                Log.d(TAG, "3 minutes on Shorts! Playing audio.")
                triggerInterrupt()
                startRepeatTimer()
            }
        }
    }

    private fun startRepeatTimer() {
        repeatTimerJob?.cancel()
        repeatTimerJob = serviceScope.launch {
            while (isOnShorts) {
                delay(5 * 60 * 1000) // 5 minutes
                if (isOnShorts) {
                    Log.d(TAG, "Still on Shorts (5m repeat). Playing audio.")
                    triggerInterrupt()
                }
            }
        }
    }

    private fun triggerInterrupt() {
        AudioHelper.play(applicationContext)
        incrementStat("interrupt_count")
        prefs.edit().putLong("last_interrupt_timestamp", System.currentTimeMillis()).apply()
        hasPlayedOnce = true
    }

    private fun recordSessionDuration() {
        if (shortsStartTime > 0) {
            val durationSeconds = (SystemClock.elapsedRealtime() - shortsStartTime) / 1000
            val totalSeconds = prefs.getLong("total_shorts_seconds", 0L) + durationSeconds
            val longestSession = prefs.getLong("longest_session_seconds", 0L)
            
            val editor = prefs.edit()
            editor.putLong("total_shorts_seconds", totalSeconds)
            if (durationSeconds > longestSession) {
                editor.putLong("longest_session_seconds", durationSeconds)
            }
            editor.apply()
        }
    }

    private fun incrementStat(key: String) {
        val current = prefs.getInt(key, 0)
        prefs.edit().putInt(key, current + 1).apply()
    }

    private fun resetAllTimers() {
        isOnShorts = false
        shortsStartTime = 0L
        hasPlayedOnce = false
        shortsAbsentCount = 0
        initialTimerJob?.cancel()
        repeatTimerJob?.cancel()
        // Note: AudioHelper.stop() is called explicitly where needed or can be added here if safe
    }

    private fun traverseNodeTree(node: AccessibilityNodeInfo?, textList: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.let { textList.add(it) }
        node.contentDescription?.toString()?.let { textList.add(it) }
        for (i in 0 until node.childCount) {
            traverseNodeTree(node.getChild(i), textList)
        }
    }

    private fun detectShorts(textList: List<String>): Boolean {
        val indicators = listOf("dislike this short", "like this short", "shorts", "reel player", "shorts shelf")
        for (text in textList) {
            val lowerText = text.lowercase()
            for (indicator in indicators) {
                if (lowerText.contains(indicator)) return true
            }
        }
        return false
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        // BUG FIX 2: Stop audio before canceling scope
        AudioHelper.stopAll()
        serviceScope.cancel()
        resetAllTimers()
        try {
            unregisterReceiver(screenOffReceiver)
            unregisterReceiver(debugReceiver)
        } catch (e: Exception) {
            // Already unregistered or never registered
        }
        super.onDestroy()
    }
}
