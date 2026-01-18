package com.smsforwarder.telegram

import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object TelegramSender {
    private const val TAG = "TelegramSender"
    private const val TELEGRAM_API_URL = "https://api.telegram.org/bot"

    fun sendMessage(config: TelegramConfig, sender: String, messageBody: String): Boolean {
        return try {
            val text = "📱 Sender: $sender\n💬 Message: $messageBody"
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
            connection.disconnect()

            if (responseCode == HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "Message sent successfully")
                true
            } else {
                Log.e(TAG, "Failed to send message. Response code: $responseCode")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message to Telegram: ${e.message}")
            false
        }
    }

    fun sendMessagesWithDelay(config: TelegramConfig, messages: List<Pair<String, String>>): List<Int> {
        val sentIndices = mutableListOf<Int>()

        messages.forEachIndexed { index, (sender, body) ->
            if (sendMessage(config, sender, body)) {
                sentIndices.add(index)
            }
            if (index < messages.size - 1) {
                Thread.sleep(2000)
            }
        }

        return sentIndices
    }
}
