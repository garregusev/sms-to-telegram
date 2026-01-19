package com.smsforwarder.telegram

import android.content.Context
import android.net.Uri
import androidx.work.Worker
import androidx.work.WorkerParameters

class SmsChecker(context: Context, params: WorkerParameters) : Worker(context, params) {
    companion object {
        private const val PREFS_NAME = "SmsForwarderPrefs"
        private const val SENT_IDS_KEY = "sent_sms_ids"

        fun checkNow(context: Context, config: TelegramConfig) {
            Logger.log(context, "Manual SMS check started")

            val unsentMessages = getUnsentMessages(context)
            if (unsentMessages.isEmpty()) {
                Logger.log(context, "No unsent messages found")
                return
            }

            Logger.log(context, "Found ${unsentMessages.size} unsent messages")

            for ((smsId, sender, body) in unsentMessages) {
                Logger.log(context, "Forwarding SMS from: $sender")
                if (TelegramSender.sendMessage(context, config, sender, body)) {
                    markSmsSent(context, smsId)
                    Logger.log(context, "SMS forwarded successfully")
                } else {
                    Logger.log(context, "Failed to forward SMS from: $sender")
                }
                Thread.sleep(2000)
            }

            Logger.log(context, "Manual SMS check completed")
        }

        private fun getUnsentMessages(context: Context): List<Triple<String, String, String>> {
            val unsentMessages = mutableListOf<Triple<String, String, String>>()

            try {
                val uri = Uri.parse("content://sms/inbox")
                val projection = arrayOf("_id", "address", "body", "date")
                val cursor = context.contentResolver.query(
                    uri, projection, null, null, "date DESC"
                )

                cursor?.use {
                    val addressIndex = it.getColumnIndexOrThrow("address")
                    val bodyIndex = it.getColumnIndexOrThrow("body")
                    val dateIndex = it.getColumnIndexOrThrow("date")

                    while (it.moveToNext()) {
                        val address = it.getString(addressIndex) ?: "Unknown"
                        val body = it.getString(bodyIndex) ?: ""
                        val date = it.getLong(dateIndex)
                        val smsId = "${address}_${date}"

                        if (!isSmsSent(context, smsId)) {
                            unsentMessages.add(Triple(smsId, address, body))
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.log(context, "Error reading SMS inbox: ${e.message}")
            }

            return unsentMessages
        }

        private fun isSmsSent(context: Context, smsId: String): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val sentIds = prefs.getStringSet(SENT_IDS_KEY, emptySet()) ?: emptySet()
            return sentIds.contains(smsId)
        }

        private fun markSmsSent(context: Context, smsId: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val sentIds = prefs.getStringSet(SENT_IDS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
            sentIds.add(smsId)
            prefs.edit().putStringSet(SENT_IDS_KEY, sentIds).apply()
        }
    }

    override fun doWork(): Result {
        Logger.log(applicationContext, "WorkManager SMS check started")

        val config = ConfigManager.getConfig(applicationContext)
        if (config == null) {
            Logger.log(applicationContext, "Config not available, skipping SMS check")
            return Result.success()
        }

        val unsentMessages = getUnsentMessages(applicationContext)
        if (unsentMessages.isEmpty()) {
            Logger.log(applicationContext, "No unsent messages found")
            return Result.success()
        }

        Logger.log(applicationContext, "Found ${unsentMessages.size} unsent messages")

        for ((smsId, sender, body) in unsentMessages) {
            Logger.log(applicationContext, "Forwarding SMS from: $sender")
            if (TelegramSender.sendMessage(applicationContext, config, sender, body)) {
                markSmsSent(applicationContext, smsId)
                Logger.log(applicationContext, "SMS forwarded successfully")
            } else {
                Logger.log(applicationContext, "Failed to forward SMS from: $sender")
            }
            Thread.sleep(2000)
        }

        Logger.log(applicationContext, "WorkManager SMS check completed")
        return Result.success()
    }
}
