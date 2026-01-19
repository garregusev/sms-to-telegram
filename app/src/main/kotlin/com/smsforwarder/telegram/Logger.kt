package com.smsforwarder.telegram

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    private const val PREFS_NAME = "SmsForwarderLogs"
    private const val LOGS_KEY = "logs"
    private const val MAX_RETENTION_DAYS = 30
    private const val MAX_LOG_ENTRIES = 1000

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private var listener: LogListener? = null

    interface LogListener {
        fun onLogUpdated(logs: String)
    }

    fun setLogListener(listener: LogListener?) {
        this.listener = listener
    }

    fun log(context: Context, message: String) {
        val prefs = getPrefs(context)
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] $message"

        val currentLogs = prefs.getString(LOGS_KEY, "") ?: ""
        val logLines = currentLogs.lines().toMutableList()

        logLines.add(0, logEntry)

        // Keep only MAX_LOG_ENTRIES
        while (logLines.size > MAX_LOG_ENTRIES) {
            logLines.removeAt(logLines.size - 1)
        }

        // Remove entries older than MAX_RETENTION_DAYS
        val cutoffTime = System.currentTimeMillis() - (MAX_RETENTION_DAYS * 24 * 60 * 60 * 1000L)
        val filteredLogs = logLines.filter { line ->
            if (line.isBlank()) return@filter false
            try {
                val dateStr = line.substringAfter("[").substringBefore("]")
                val logDate = dateFormat.parse(dateStr)
                logDate != null && logDate.time >= cutoffTime
            } catch (e: Exception) {
                true // Keep entries we can't parse
            }
        }

        val newLogs = filteredLogs.joinToString("\n")
        prefs.edit().putString(LOGS_KEY, newLogs).apply()

        listener?.onLogUpdated(newLogs)

        android.util.Log.d("SmsForwarder", message)
    }

    fun getLogs(context: Context): String {
        return getPrefs(context).getString(LOGS_KEY, "") ?: ""
    }

    fun clearLogs(context: Context) {
        getPrefs(context).edit().remove(LOGS_KEY).apply()
        listener?.onLogUpdated("")
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
