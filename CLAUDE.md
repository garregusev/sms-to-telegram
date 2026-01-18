# SMS to Telegram Forwarder - Architecture Documentation

## Overview

This Android app forwards SMS messages to Telegram using two complementary mechanisms:
1. **Real-time forwarding** via BroadcastReceiver when SMS arrives
2. **Periodic backup check** via WorkManager every hour for missed messages

## File Structure

```
app/src/main/
├── AndroidManifest.xml
├── kotlin/com/smsforwarder/telegram/
│   ├── App.kt              # Application entry point
│   ├── ConfigReader.kt     # JSON config file reader
│   ├── SmsReceiver.kt      # BroadcastReceiver for incoming SMS
│   ├── SmsChecker.kt       # WorkManager periodic worker
│   └── TelegramSender.kt   # Telegram API client
└── res/                    # Resources (icons, strings, themes)
```

### File Purposes

**App.kt**
- Extends `Application` class
- Initializes on app startup
- Schedules the periodic WorkManager task with `ExistingPeriodicWorkPolicy.KEEP`

**ConfigReader.kt**
- Reads JSON config from `/sdcard/Download/sms-forwarder-config.json`
- Returns `Config` data class with `botToken` and `chatId`
- Returns `null` if file missing or invalid (no crash)

**SmsReceiver.kt**
- Registered in manifest for `android.provider.Telephony.SMS_RECEIVED`
- Extracts sender and message body from PDU
- Calls TelegramSender and marks as sent in SharedPreferences

**SmsChecker.kt**
- WorkManager `Worker` subclass
- Queries SMS inbox via ContentResolver
- Filters unsent messages using SharedPreferences
- Sends each with 2-second delay between batches

**TelegramSender.kt**
- Static utility for Telegram API calls
- Uses `HttpURLConnection` (no external HTTP libraries)
- Manages SharedPreferences for sent message tracking

## How SMS Receiving Works (BroadcastReceiver)

1. Android system broadcasts `SMS_RECEIVED` intent when SMS arrives
2. `SmsReceiver.onReceive()` is triggered by the system
3. PDU (Protocol Data Unit) is extracted from intent extras
4. `SmsMessage.createFromPdu()` parses sender and body
5. `TelegramSender.sendSms()` forwards to Telegram
6. Message ID stored in SharedPreferences to prevent duplicates

```kotlin
// Intent contains PDU array
val pdus = intent.extras?.get("pdus") as? Array<*>
for (pdu in pdus) {
    val message = SmsMessage.createFromPdu(pdu as ByteArray, format)
    val sender = message.displayOriginatingAddress
    val body = message.messageBody
}
```

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

## How Deduplication Works

Messages are tracked using SharedPreferences with composite keys:

**Key Format:** `{sender}_{timestamp}`

Example: `+15551234567_1699876543210`

**Storage Location:** Default SharedPreferences file

**Tracking Flow:**
1. Before sending, check if key exists: `prefs.contains(key)`
2. If exists, skip (already sent)
3. If not exists, send to Telegram
4. After successful send, store: `prefs.edit().putBoolean(key, true).apply()`

This approach handles:
- Duplicate broadcasts from BroadcastReceiver
- Messages already sent in real-time being re-scanned by hourly check
- App restarts (SharedPreferences persists)

## Config File Format and Location

**Location:** `/sdcard/Download/sms-forwarder-config.json`

This path maps to the device's internal storage Download folder, typically:
- `/storage/emulated/0/Download/sms-forwarder-config.json`

**Format:**
```json
{
  "bot_token": "123456789:ABCdefGHIjklMNOpqrsTUVwxyz",
  "chat_id": "987654321"
}
```

**Reading Logic:**
```kotlin
val file = File("/sdcard/Download/sms-forwarder-config.json")
if (!file.exists()) return null

val json = JSONObject(file.readText())
return Config(
    botToken = json.getString("bot_token"),
    chatId = json.getString("chat_id")
)
```

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

**Implementation:**
```kotlin
val url = URL("https://api.telegram.org/bot$botToken/sendMessage")
val connection = url.openConnection() as HttpURLConnection
connection.requestMethod = "POST"
connection.doOutput = true

val postData = "chat_id=${URLEncoder.encode(chatId, "UTF-8")}" +
               "&text=${URLEncoder.encode(text, "UTF-8")}"

connection.outputStream.use { it.write(postData.toByteArray()) }
val responseCode = connection.responseCode
```

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
- No external HTTP libraries (uses `java.net.HttpURLConnection`)
- No external JSON libraries (uses `org.json.JSONObject` from Android SDK)
