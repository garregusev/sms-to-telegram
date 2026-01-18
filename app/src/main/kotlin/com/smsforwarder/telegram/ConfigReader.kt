package com.smsforwarder.telegram

import android.os.Environment
import android.util.Log
import org.json.JSONObject
import java.io.File

data class TelegramConfig(
    val botToken: String,
    val chatId: String
)

object ConfigReader {
    private const val TAG = "ConfigReader"
    private const val CONFIG_FILE_NAME = "sms-forwarder-config.json"

    fun readConfig(): TelegramConfig? {
        return try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val configFile = File(downloadDir, CONFIG_FILE_NAME)

            if (!configFile.exists()) {
                Log.e(TAG, "Config file not found: ${configFile.absolutePath}")
                return null
            }

            val jsonContent = configFile.readText()
            val json = JSONObject(jsonContent)

            val botToken = json.optString("bot_token", "")
            val chatId = json.optString("chat_id", "")

            if (botToken.isEmpty() || chatId.isEmpty()) {
                Log.e(TAG, "Config file missing bot_token or chat_id")
                return null
            }

            TelegramConfig(botToken, chatId)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading config file: ${e.message}")
            null
        }
    }
}
