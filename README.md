# SMS to Telegram Forwarder

An Android app that automatically forwards incoming SMS messages to a Telegram chat.

## Features

- Instant forwarding of incoming SMS via BroadcastReceiver
- Hourly background check for any missed messages via WorkManager
- Duplicate prevention using message tracking
- In-app configuration (no config file needed)
- Built-in logging for debugging
- Foreground service for reliable operation
- Minimum SDK: Android 13 (API 33)

## Installation

1. Download the APK from the [Releases](../../releases) page
2. Install the APK on your Android device (enable "Install from unknown sources" if prompted)
3. Open the app

## Setup

1. **Grant Permissions**
   - Tap "Grant Permissions" button
   - Allow SMS permissions (to receive and read messages)
   - Allow notification permission (for the foreground service)

2. **Configure Telegram**
   - Enter your Telegram Bot Token
   - Enter your Chat ID
   - Tap "Save"

3. **Test the Connection**
   - Tap "Send Test Message"
   - Check if you receive "Test from SMS Forwarder" in Telegram

## Getting Telegram Credentials

### How to get your Bot Token

1. Open Telegram and search for [@BotFather](https://t.me/BotFather)
2. Send `/newbot` command
3. Follow the prompts to name your bot
4. BotFather will give you a token like `123456789:ABCdefGHIjklMNOpqrsTUVwxyz`
5. Copy this token to the app

### How to get your Chat ID

**Option 1: Using @userinfobot**
1. Search for [@userinfobot](https://t.me/userinfobot) on Telegram
2. Start a chat with it
3. It will reply with your user ID - this is your Chat ID

**Option 2: Using @RawDataBot**
1. Search for [@RawDataBot](https://t.me/RawDataBot) on Telegram
2. Send any message to it
3. Look for `"id":` in the response under `"chat"` section

**Option 3: For group chats**
1. Add your bot to the group
2. Send a message in the group
3. Visit `https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getUpdates`
4. Find the `chat.id` value (group IDs are negative numbers)

**Important:** After creating your bot, you must start a conversation with it (send `/start`) before it can send you messages.

## App Interface

The app provides a simple interface with:

- **Permissions Section**: Shows status of SMS and notification permissions
- **Configuration Section**: Enter and save your Telegram bot token and chat ID
- **Actions Section**:
  - Send Test Message - verify your configuration works
  - Check SMS Now - manually trigger an SMS inbox check
  - Shows next scheduled automatic check time
- **Logs Section**: View activity logs for debugging (selectable/copyable text)

## Permissions Explanation

| Permission | Purpose |
|------------|---------|
| `RECEIVE_SMS` | Detect incoming SMS messages in real-time |
| `READ_SMS` | Read SMS content and check inbox for missed messages |
| `INTERNET` | Send messages to Telegram API |
| `POST_NOTIFICATIONS` | Show persistent notification for foreground service |
| `FOREGROUND_SERVICE` | Keep the app running reliably in background |

## Message Format

Forwarded messages appear in Telegram as:

```
📱 Sender: +1234567890
💬 Message: Your SMS content here
```

## How It Works

1. **Real-time Forwarding**: When an SMS arrives, the BroadcastReceiver immediately forwards it to Telegram
2. **Backup Check**: Every hour, WorkManager checks the SMS inbox for any messages that might have been missed
3. **Deduplication**: Each message is tracked using `{sender}_{timestamp}` keys in SharedPreferences to prevent duplicates
4. **Foreground Service**: Ensures the app stays active and responsive

## Troubleshooting

- **Messages not forwarding**: Check that permissions are granted and configuration is saved
- **Bot not responding**: Make sure you've started a conversation with your bot first (send `/start`)
- **Permission denied**: Tap "Grant Permissions" button and allow all permissions
- **Check the logs**: The log section shows detailed information about what's happening

## Building from Source

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`

## License

MIT License
