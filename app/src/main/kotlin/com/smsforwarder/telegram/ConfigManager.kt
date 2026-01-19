package com.smsforwarder.telegram

import android.content.Context
import android.content.SharedPreferences

data class TelegramConfig(
    val botToken: String,
    val chatId: String
)

object ConfigManager {
    private const val PREFS_NAME = "SmsForwarderConfig"
    private const val KEY_BOT_TOKEN = "bot_token"
    private const val KEY_CHAT_ID = "chat_id"

    fun saveConfig(context: Context, botToken: String, chatId: String) {
        getPrefs(context).edit()
            .putString(KEY_BOT_TOKEN, botToken)
            .putString(KEY_CHAT_ID, chatId)
            .apply()
    }

    fun getConfig(context: Context): TelegramConfig? {
        val prefs = getPrefs(context)
        val botToken = prefs.getString(KEY_BOT_TOKEN, "") ?: ""
        val chatId = prefs.getString(KEY_CHAT_ID, "") ?: ""

        if (botToken.isEmpty() || chatId.isEmpty()) {
            return null
        }

        return TelegramConfig(botToken, chatId)
    }

    fun getBotToken(context: Context): String {
        return getPrefs(context).getString(KEY_BOT_TOKEN, "") ?: ""
    }

    fun getChatId(context: Context): String {
        return getPrefs(context).getString(KEY_CHAT_ID, "") ?: ""
    }

    fun isConfigured(context: Context): Boolean {
        val prefs = getPrefs(context)
        val botToken = prefs.getString(KEY_BOT_TOKEN, "") ?: ""
        val chatId = prefs.getString(KEY_CHAT_ID, "") ?: ""
        return botToken.isNotEmpty() && chatId.isNotEmpty()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
