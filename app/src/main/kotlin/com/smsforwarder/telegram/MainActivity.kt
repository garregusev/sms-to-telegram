package com.smsforwarder.telegram

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(), Logger.LogListener {

    private lateinit var textSmsPermission: TextView
    private lateinit var textNotificationPermission: TextView
    private lateinit var editBotToken: TextInputEditText
    private lateinit var editChatId: TextInputEditText
    private lateinit var textNextCheck: TextView
    private lateinit var textLogs: TextView
    private lateinit var buttonCheckSms: Button
    private lateinit var buttonStopSending: Button
    private lateinit var textSendingProgress: TextView

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    @Volatile
    private var isSending = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach { (permission, granted) ->
            val status = if (granted) "granted" else "denied"
            Logger.log(this, "Permission $permission: $status")
        }
        updatePermissionStatus()

        // Initialize first run after SMS permission is granted
        if (checkPermission(Manifest.permission.READ_SMS)) {
            initializeFirstRunIfNeeded()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        loadConfig()
        updatePermissionStatus()
        updateNextCheckTime()

        Logger.setLogListener(this)
        textLogs.text = Logger.getLogs(this)

        Logger.log(this, "App started")

        // Initialize first run if SMS permission is already granted
        if (checkPermission(Manifest.permission.READ_SMS)) {
            initializeFirstRunIfNeeded()
        }

        // Start foreground service
        ForwarderService.start(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.setLogListener(null)
    }

    override fun onLogUpdated(logs: String) {
        runOnUiThread {
            textLogs.text = logs
        }
    }

    private fun initViews() {
        textSmsPermission = findViewById(R.id.textSmsPermission)
        textNotificationPermission = findViewById(R.id.textNotificationPermission)
        editBotToken = findViewById(R.id.editBotToken)
        editChatId = findViewById(R.id.editChatId)
        textNextCheck = findViewById(R.id.textNextCheck)
        textLogs = findViewById(R.id.textLogs)
        buttonCheckSms = findViewById(R.id.buttonCheckSms)
        buttonStopSending = findViewById(R.id.buttonStopSending)
        textSendingProgress = findViewById(R.id.textSendingProgress)
    }

    private fun setupListeners() {
        findViewById<Button>(R.id.buttonGrantPermissions).setOnClickListener {
            requestPermissions()
        }

        findViewById<Button>(R.id.buttonSaveConfig).setOnClickListener {
            saveConfig()
        }

        findViewById<Button>(R.id.buttonTestMessage).setOnClickListener {
            sendTestMessage()
        }

        buttonCheckSms.setOnClickListener {
            triggerSmsCheck()
        }

        buttonStopSending.setOnClickListener {
            stopSending()
        }

        findViewById<Button>(R.id.buttonClearLogs).setOnClickListener {
            Logger.clearLogs(this)
            Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadConfig() {
        editBotToken.setText(ConfigManager.getBotToken(this))
        editChatId.setText(ConfigManager.getChatId(this))
    }

    private fun saveConfig() {
        val botToken = editBotToken.text?.toString()?.trim() ?: ""
        val chatId = editChatId.text?.toString()?.trim() ?: ""

        if (botToken.isEmpty() || chatId.isEmpty()) {
            Toast.makeText(this, "Please fill in both fields", Toast.LENGTH_SHORT).show()
            return
        }

        ConfigManager.saveConfig(this, botToken, chatId)
        Logger.log(this, "Configuration saved")
        Toast.makeText(this, "Configuration saved", Toast.LENGTH_SHORT).show()
    }

    private fun updatePermissionStatus() {
        val smsGranted = checkPermission(Manifest.permission.RECEIVE_SMS) &&
                checkPermission(Manifest.permission.READ_SMS)
        val notificationGranted = checkPermission(Manifest.permission.POST_NOTIFICATIONS)

        textSmsPermission.text = if (smsGranted) {
            "SMS: ✅ granted"
        } else {
            "SMS: ❌ denied"
        }

        textNotificationPermission.text = if (notificationGranted) {
            "Notifications: ✅ granted"
        } else {
            "Notifications: ❌ denied"
        }
    }

    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.POST_NOTIFICATIONS
        )
        permissionLauncher.launch(permissions)
    }

    private fun initializeFirstRunIfNeeded() {
        if (!SmsChecker.isInitialized(this)) {
            thread {
                SmsChecker.initializeFirstRun(this)
            }
        }
    }

    private fun sendTestMessage() {
        val config = ConfigManager.getConfig(this)
        if (config == null) {
            Toast.makeText(this, "Please configure bot token and chat ID first", Toast.LENGTH_SHORT).show()
            return
        }

        Logger.log(this, "Sending test message...")
        Toast.makeText(this, "Sending test message...", Toast.LENGTH_SHORT).show()

        thread {
            val success = TelegramSender.sendTestMessage(this, config)
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "Test message sent!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to send test message", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun triggerSmsCheck() {
        if (isSending) {
            Toast.makeText(this, "Already sending...", Toast.LENGTH_SHORT).show()
            return
        }

        val config = ConfigManager.getConfig(this)
        if (config == null) {
            Toast.makeText(this, "Please configure settings first", Toast.LENGTH_SHORT).show()
            return
        }

        Logger.log(this, "Manual SMS check triggered")
        setSendingState(true)

        thread {
            SmsChecker.checkNow(this, config, object : SmsChecker.Companion.ProgressListener {
                override fun onProgress(current: Int, total: Int) {
                    runOnUiThread {
                        textSendingProgress.text = "Sending $current/$total..."
                    }
                }

                override fun onComplete() {
                    runOnUiThread {
                        setSendingState(false)
                        Toast.makeText(this@MainActivity, "SMS check completed", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
    }

    private fun stopSending() {
        SmsChecker.cancelSending()
        Logger.log(this, "Stop requested by user")
        Toast.makeText(this, "Stopping...", Toast.LENGTH_SHORT).show()
    }

    private fun setSendingState(sending: Boolean) {
        isSending = sending
        buttonCheckSms.visibility = if (sending) View.GONE else View.VISIBLE
        buttonStopSending.visibility = if (sending) View.VISIBLE else View.GONE
        textSendingProgress.visibility = if (sending) View.VISIBLE else View.GONE
        if (!sending) {
            textSendingProgress.text = ""
        }
    }

    private fun updateNextCheckTime() {
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(App.WORK_NAME)
            .observe(this) { workInfos ->
                val workInfo = workInfos?.firstOrNull()
                if (workInfo != null && workInfo.state == WorkInfo.State.ENQUEUED) {
                    val nextRun = workInfo.nextScheduleTimeMillis
                    if (nextRun > 0) {
                        val time = timeFormat.format(Date(nextRun))
                        textNextCheck.text = "Next scheduled check: $time"
                    } else {
                        textNextCheck.text = "Next scheduled check: ~1 hour"
                    }
                } else {
                    textNextCheck.text = "Next scheduled check: --:--"
                }
            }
    }
}
