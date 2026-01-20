package com.smsforwarder.telegram

import android.content.Context
import android.net.Uri
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.atomic.AtomicBoolean

class SmsChecker(context: Context, params: WorkerParameters) : Worker(context, params) {
    companion object {
        private const val PREFS_NAME = "SmsForwarderPrefs"
        private const val SENT_IDS_KEY = "sent_sms_ids"
        private const val INITIALIZED_KEY = "is_initialized"
        private const val MAX_AGE_HOURS = 48
        private const val MAX_BATCH_SIZE = 20

        @Volatile
        var isCancelled = AtomicBoolean(false)
            private set

        interface ProgressListener {
            fun onProgress(current: Int, total: Int)
            fun onComplete()
        }

        fun cancelSending() {
            isCancelled.set(true)
        }

        fun initializeFirstRun(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(INITIALIZED_KEY, false)) {
                return
            }

            Logger.log(context, "First run initialization started")
            val existingMessages = getAllRecentMessages(context)

            if (existingMessages.isNotEmpty()) {
                val sentIds = prefs.getStringSet(SENT_IDS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
                existingMessages.forEach { (smsId, _, _) ->
                    sentIds.add(smsId)
                }
                prefs.edit()
                    .putStringSet(SENT_IDS_KEY, sentIds)
                    .putBoolean(INITIALIZED_KEY, true)
                    .apply()
                Logger.log(context, "First run: marked ${existingMessages.size} existing SMS as processed")
            } else {
                prefs.edit().putBoolean(INITIALIZED_KEY, true).apply()
                Logger.log(context, "First run: no existing SMS found")
            }
        }

        fun isInitialized(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(INITIALIZED_KEY, false)
        }

        fun checkNow(context: Context, config: TelegramConfig, progressListener: ProgressListener? = null) {
            isCancelled.set(false)
            Logger.log(context, "Manual SMS check started")

            val unsentMessages = getUnsentMessages(context)
            if (unsentMessages.isEmpty()) {
                Logger.log(context, "No unsent messages found")
                progressListener?.onComplete()
                return
            }

            val totalFound = unsentMessages.size
            val messagesToSend = if (totalFound > MAX_BATCH_SIZE) {
                Logger.log(context, "Found $totalFound messages, sending first $MAX_BATCH_SIZE")
                unsentMessages.take(MAX_BATCH_SIZE)
            } else {
                Logger.log(context, "Found $totalFound unsent messages")
                unsentMessages
            }

            val total = messagesToSend.size
            var sent = 0

            for ((smsId, sender, body) in messagesToSend) {
                if (isCancelled.get()) {
                    Logger.log(context, "Sending cancelled by user")
                    progressListener?.onComplete()
                    return
                }

                sent++
                progressListener?.onProgress(sent, total)

                Logger.log(context, "Forwarding SMS from: $sender ($sent/$total)")
                if (TelegramSender.sendMessage(context, config, sender, body)) {
                    markSmsSent(context, smsId)
                    Logger.log(context, "SMS forwarded successfully")
                } else {
                    Logger.log(context, "Failed to forward SMS from: $sender")
                }

                if (sent < total && !isCancelled.get()) {
                    Thread.sleep(2000)
                }
            }

            Logger.log(context, "Manual SMS check completed")
            progressListener?.onComplete()
        }

        private fun getAllRecentMessages(context: Context): List<Triple<String, String, String>> {
            val messages = mutableListOf<Triple<String, String, String>>()
            val cutoffTime = System.currentTimeMillis() - (MAX_AGE_HOURS * 60 * 60 * 1000L)

            try {
                val uri = Uri.parse("content://sms/inbox")
                val projection = arrayOf("_id", "address", "body", "date")
                val selection = "date > ?"
                val selectionArgs = arrayOf(cutoffTime.toString())
                val cursor = context.contentResolver.query(
                    uri, projection, selection, selectionArgs, "date DESC"
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
                        messages.add(Triple(smsId, address, body))
                    }
                }
            } catch (e: Exception) {
                Logger.log(context, "Error reading SMS inbox: ${e.message}")
            }

            return messages
        }

        private fun getUnsentMessages(context: Context): List<Triple<String, String, String>> {
            val unsentMessages = mutableListOf<Triple<String, String, String>>()
            val cutoffTime = System.currentTimeMillis() - (MAX_AGE_HOURS * 60 * 60 * 1000L)

            try {
                val uri = Uri.parse("content://sms/inbox")
                val projection = arrayOf("_id", "address", "body", "date")
                val selection = "date > ?"
                val selectionArgs = arrayOf(cutoffTime.toString())
                val cursor = context.contentResolver.query(
                    uri, projection, selection, selectionArgs, "date DESC"
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

        fun markSmsSent(context: Context, smsId: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val sentIds = prefs.getStringSet(SENT_IDS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
            sentIds.add(smsId)
            prefs.edit().putStringSet(SENT_IDS_KEY, sentIds).apply()
        }
    }

    override fun doWork(): Result {
        Logger.log(applicationContext, "WorkManager SMS check started")

        if (!isInitialized(applicationContext)) {
            initializeFirstRun(applicationContext)
        }

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

        val totalFound = unsentMessages.size
        val messagesToSend = if (totalFound > MAX_BATCH_SIZE) {
            Logger.log(applicationContext, "Found $totalFound messages, sending first $MAX_BATCH_SIZE")
            unsentMessages.take(MAX_BATCH_SIZE)
        } else {
            Logger.log(applicationContext, "Found $totalFound unsent messages")
            unsentMessages
        }

        for ((smsId, sender, body) in messagesToSend) {
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
