package com.example.myapplication

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var enableButton: Button
    private lateinit var testAudioButton: Button
    private lateinit var stopAudioButton: Button
    private lateinit var tvShortsOpened: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var tvInterrupted: TextView
    private lateinit var tvTimeSaved: TextView
    private lateinit var tvLongestSession: TextView
    private lateinit var tvLastInterrupted: TextView
    private lateinit var resetButton: Button

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("shorts_stats", Context.MODE_PRIVATE)

        statusTextView = findViewById(R.id.statusTextView)
        enableButton = findViewById(R.id.enableButton)
        testAudioButton = findViewById(R.id.testAudioButton)
        stopAudioButton = findViewById(R.id.stopAudioButton)
        tvShortsOpened = findViewById(R.id.tvShortsOpened)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        tvInterrupted = findViewById(R.id.tvInterrupted)
        tvTimeSaved = findViewById(R.id.tvTimeSaved)
        tvLongestSession = findViewById(R.id.tvLongestSession)
        tvLastInterrupted = findViewById(R.id.tvLastInterrupted)
        resetButton = findViewById(R.id.resetButton)

        enableButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        testAudioButton.setOnClickListener {
            AudioHelper.play(this)
        }

        stopAudioButton.setOnClickListener {
            AudioHelper.stop()
        }

        resetButton.setOnClickListener {
            showResetConfirmation()
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        loadStats()
    }

    private fun updateServiceStatus() {
        if (isAccessibilityServiceEnabled()) {
            statusTextView.text = getString(R.string.service_enabled)
        } else {
            statusTextView.text = getString(R.string.service_disabled)
        }
    }

    private fun loadStats() {
        val shortsOpened = prefs.getInt("shorts_open_count", 0)
        val totalSeconds = prefs.getLong("total_shorts_seconds", 0L)
        val interruptCount = prefs.getInt("interrupt_count", 0)
        val longestSessionSeconds = prefs.getLong("longest_session_seconds", 0L)
        val lastInterrupt = prefs.getLong("last_interrupt_timestamp", 0L)

        tvShortsOpened.text = shortsOpened.toString()
        tvTotalTime.text = formatDuration(totalSeconds)
        tvInterrupted.text = interruptCount.toString()
        tvLongestSession.text = formatDuration(longestSessionSeconds)
        
        // Time Saved estimate: interrupt_count * 4 minutes
        val minutesSaved = interruptCount * 4
        tvTimeSaved.text = "~${minutesSaved}m"

        tvLastInterrupted.text = formatTimestamp(lastInterrupt)
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return "N/A"
        
        val now = Calendar.getInstance()
        val time = Calendar.getInstance().apply { timeInMillis = timestamp }
        
        val format = SimpleDateFormat("h:mm a", Locale.getDefault())
        val timeString = format.format(Date(timestamp))

        return when {
            isSameDay(now, time) -> getString(R.string.today_at, timeString)
            isYesterday(now, time) -> getString(R.string.yesterday_at, timeString)
            else -> SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(now: Calendar, then: Calendar): Boolean {
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return isSameDay(yesterday, then)
    }

    private fun showResetConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_stats)
            .setMessage(R.string.confirm_reset)
            .setPositiveButton(R.string.yes) { _, _ ->
                prefs.edit().clear().apply()
                loadStats()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == packageName &&
                service.resolveInfo.serviceInfo.name == ShortsAccessibilityService::class.java.name) {
                return true
            }
        }
        return false
    }
}
