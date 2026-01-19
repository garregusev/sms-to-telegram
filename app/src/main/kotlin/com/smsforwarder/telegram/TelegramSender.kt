package com.smsforwarder.telegram

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object TelegramSender {
    private const val TELEGRAM_API_URL = "https://api.telegram.org/bot"

    fun sendMessage(context: Context, config: TelegramConfig, sender: String, messageBody: String): Boolean {
        val text = "📱 Sender: $sender\n💬 Message: $messageBody"
        return sendToTelegram(context, config, text)
    }

    fun sendTestMessage(context: Context, config: TelegramConfig): Boolean {
        val text = "Test from SMS Forwarder"
        Logger.log(context, "Sending test message to Telegram...")
        return sendToTelegram(context, config, text)
    }

    private fun sendToTelegram(context: Context, config: TelegramConfig, text: String): Boolean {
        return try {
            Logger.log(context, "Telegram API request started")

            val url = URL("${TELEGRAM_API_URL}${config.botToken}/sendMessage")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val postData = "chat_id=${URLEncoder.encode(config.chatId, "UTF-8")}&text=${URLEncoder.encode(text, "UTF-8")}"

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(postData)
                writer.flush()
            }

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                Logger.log(context, "Telegram send success (HTTP $responseCode)")
                connection.disconnect()
                true
            } else {
                val errorResponse = try {
                    BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                } catch (e: Exception) {
                    "Unable to read error response"
                }
                Logger.log(context, "Telegram send failed (HTTP $responseCode): $errorResponse")
                connection.disconnect()
                false
            }
        } catch (e: Exception) {
            Logger.log(context, "Telegram send error: ${e.message}")
            false
        }
    }
}
