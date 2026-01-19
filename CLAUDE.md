# SMS to Telegram Forwarder - Architecture Documentation

## Overview

This Android app forwards SMS messages to Telegram using multiple mechanisms:
1. **Real-time forwarding** via BroadcastReceiver when SMS arrives
2. **Periodic backup check** via WorkManager every hour for missed messages
3. **Foreground service** for reliable background operation
4. **In-app UI** for configuration and debugging

## File Structure

```
app/src/main/
├── AndroidManifest.xml
├── kotlin/com/smsforwarder/telegram/
│   ├── App.kt              # Application entry point
│   ├── MainActivity.kt     # Main UI activity
│   ├── ConfigManager.kt    # SharedPreferences config storage
│   ├── Logger.kt           # Logging utility with SharedPreferences
│   ├── ForwarderService.kt # Foreground service
│   ├── SmsReceiver.kt      # BroadcastReceiver for incoming SMS
│   ├── SmsChecker.kt       # WorkManager periodic worker
│   └── TelegramSender.kt   # Telegram API client
└── res/
    ├── layout/
    │   └── activity_main.xml  # Main UI layout
    └── values/                # Resources (colors, strings, themes)
```

### File Purposes

**App.kt**
- Extends `Application` class
- Initializes on app startup
- Schedules the periodic WorkManager task with `ExistingPeriodicWorkPolicy.KEEP`

**MainActivity.kt**
- Main UI activity with configuration and debugging interface
- Displays permission status (SMS, Notifications)
- Provides config input fields (bot token, chat ID)
- Action buttons: Send Test Message, Check SMS Now, Clear Logs
- Shows next scheduled WorkManager check time
- Displays scrollable log view with selectable text
- Starts the foreground service on launch

**ConfigManager.kt**
- Stores configuration in SharedPreferences (`SmsForwarderConfig`)
- Provides `saveConfig()`, `getConfig()`, `isConfigured()` methods
- Returns `TelegramConfig` data class with `botToken` and `chatId`
- Returns `null` if config not set

**Logger.kt**
- Writes timestamped logs to SharedPreferences (`SmsForwarderLogs`)
- Format: `[YYYY-MM-DD HH:MM:SS] message`
- Max 1000 entries, 30-day retention
- Provides `LogListener` interface for real-time UI updates
- Methods: `log()`, `getLogs()`, `clearLogs()`

**ForwarderService.kt**
- Foreground service with persistent notification
- Shows "SMS Forwarder running" notification
- Uses `FOREGROUND_SERVICE_SPECIAL_USE` for SMS forwarding use case
- Static `start()` and `stop()` methods for control

**SmsReceiver.kt**
- Registered in manifest for `android.provider.Telephony.SMS_RECEIVED`
- Extracts sender and message body using `Telephony.Sms.Intents`
- Calls TelegramSender and marks as sent in SharedPreferences
- Logs all events via Logger

**SmsChecker.kt**
- WorkManager `Worker` subclass for periodic inbox checks
- Queries SMS inbox via ContentResolver
- Filters unsent messages using SharedPreferences
- Sends each with 2-second delay between batches
- Static `checkNow()` method for manual triggering
- Logs all events via Logger

**TelegramSender.kt**
- Static utility for Telegram API calls
- Uses `HttpURLConnection` (no external HTTP libraries)
- `sendMessage()` for SMS forwarding
- `sendTestMessage()` for configuration testing
- Logs success/failure with HTTP response codes

## How SMS Receiving Works (BroadcastReceiver)

1. Android system broadcasts `SMS_RECEIVED` intent when SMS arrives
2. `SmsReceiver.onReceive()` is triggered by the system
3. Messages extracted via `Telephony.Sms.Intents.getMessagesFromIntent()`
4. Config loaded from SharedPreferences via ConfigManager
5. `TelegramSender.sendMessage()` forwards to Telegram
6. Message ID stored in SharedPreferences to prevent duplicates
7. All events logged via Logger

## How Hourly Check Works (WorkManager)

1. `App.onCreate()` schedules periodic work:
   ```kotlin
   PeriodicWorkRequestBuilder<SmsChecker>(1, TimeUnit.HOURS)
   ```
2. `ExistingPeriodicWorkPolicy.KEEP` ensures only one instance runs
3. `SmsChecker.doWork()` queries inbox via ContentResolver:
   ```kotlin
   contentResolver.query(Uri.parse("content://sms/inbox"), ...)
   ```
4. Each SMS is checked against SharedPreferences
5. Unsent messages are forwarded with 2-second delays
6. Returns `Result.success()` to signal completion
7. All events logged via Logger

## How Deduplication Works

Messages are tracked using SharedPreferences with composite keys:

**Key Format:** `{sender}_{timestamp}`

Example: `+15551234567_1699876543210`

**Storage Location:** SharedPreferences file `SmsForwarderPrefs`

**Tracking Flow:**
1. Before sending, check if key exists in StringSet
2. If exists, skip (already sent)
3. If not exists, send to Telegram
4. After successful send, add to StringSet

This approach handles:
- Duplicate broadcasts from BroadcastReceiver
- Messages already sent in real-time being re-scanned by hourly check
- App restarts (SharedPreferences persists)

## Configuration Storage

**Location:** SharedPreferences file `SmsForwarderConfig`

**Keys:**
- `bot_token` - Telegram bot token string
- `chat_id` - Telegram chat ID string

**Reading Logic:**
```kotlin
val prefs = context.getSharedPreferences("SmsForwarderConfig", MODE_PRIVATE)
val botToken = prefs.getString("bot_token", "") ?: ""
val chatId = prefs.getString("chat_id", "") ?: ""
if (botToken.isEmpty() || chatId.isEmpty()) return null
return TelegramConfig(botToken, chatId)
```

## Logging System

**Location:** SharedPreferences file `SmsForwarderLogs`

**Format:** `[YYYY-MM-DD HH:MM:SS] message`

**Events logged:**
- App started
- Config saved
- Permission granted/denied
- SMS received (with sender)
- Telegram send attempt
- Telegram send success/failure (with HTTP code and error)
- WorkManager check started/completed
- Service started/stopped

**Retention:**
- Max 1000 log entries
- Entries older than 30 days automatically removed

## Telegram API Usage

**Endpoint:** `https://api.telegram.org/bot{token}/sendMessage`

**Method:** HTTP POST

**Content-Type:** `application/x-www-form-urlencoded`

**Parameters:**
- `chat_id` - Target chat/user ID
- `text` - Message content (URL-encoded)

**Message Format:**
```
📱 Sender: {sender_address}
💬 Message: {message_body}
```

**Test Message:** `Test from SMS Forwarder`

**Batch Delay:** 2 seconds between messages when sending multiple (prevents rate limiting)

## Build Process (GitHub Actions)

**Workflow File:** `.github/workflows/build.yml`

**Trigger:** Push to `main` branch

**Steps:**
1. Checkout repository
2. Set up JDK 17 (Temurin distribution)
3. Grant execute permission to gradlew
4. Build release APK: `./gradlew assembleRelease`
5. Upload APK as workflow artifact

**Artifact:** `app-release-unsigned.apk`

Note: Release builds are unsigned. For production, configure signing in `app/build.gradle.kts` or sign manually with `apksigner`.

## Dependencies

- **AndroidX WorkManager** (`androidx.work:work-runtime-ktx:2.9.0`) - Periodic background tasks
- **AndroidX Core KTX** (`androidx.core:core-ktx:1.12.0`) - Kotlin extensions
- **AndroidX AppCompat** (`androidx.appcompat:appcompat:1.6.1`) - Backward-compatible Activity
- **Material Components** (`com.google.android.material:material:1.11.0`) - UI components
- **AndroidX Activity KTX** (`androidx.activity:activity-ktx:1.8.2`) - Activity result APIs
- No external HTTP libraries (uses `java.net.HttpURLConnection`)

## Permissions

| Permission | Purpose |
|------------|---------|
| `RECEIVE_SMS` | Detect incoming SMS messages in real-time |
| `READ_SMS` | Read SMS content and check inbox for missed messages |
| `INTERNET` | Send messages to Telegram API |
| `POST_NOTIFICATIONS` | Show persistent notification for foreground service |
| `FOREGROUND_SERVICE` | Run service in foreground |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Special use case for SMS forwarding |
