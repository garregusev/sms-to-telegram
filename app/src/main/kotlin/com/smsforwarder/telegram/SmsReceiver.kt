package com.smsforwarder.telegram

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlin.concurrent.thread

class SmsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SmsReceiver"
        private const val PREFS_NAME = "SmsForwarderPrefs"
        private const val SENT_IDS_KEY = "sent_sms_ids"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            return
        }

        val config = ConfigReader.readConfig()
        if (config == null) {
            Log.e(TAG, "Config not available, cannot forward SMS")
            return
        }

        thread {
            for (sms in messages) {
                val sender = sms.displayOriginatingAddress ?: "Unknown"
                val body = sms.displayMessageBody ?: ""
                val timestamp = sms.timestampMillis
                val smsId = "${sender}_${timestamp}"

                if (isSmsSent(context, smsId)) {
                    Log.d(TAG, "SMS already sent: $smsId")
                    continue
                }

                if (TelegramSender.sendMessage(config, sender, body)) {
                    markSmsSent(context, smsId)
                    Log.d(TAG, "SMS forwarded successfully: $smsId")
                }
            }
        }
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
