# SMS to Telegram Forwarder

An Android app that automatically forwards incoming SMS messages to a Telegram chat.

## Features

- Instant forwarding of incoming SMS via BroadcastReceiver
- Hourly background check for any missed messages via WorkManager
- Duplicate prevention using message tracking
- Simple JSON configuration file
- No external dependencies (except AndroidX WorkManager)
- Minimum SDK: Android 13 (API 33)

## Installation

1. Download the APK from the [Releases](../../releases) page
2. Install the APK on your Android device (enable "Install from unknown sources" if prompted)
3. Open the app once to initialize the background service
4. Grant the required permissions when prompted:
   - **SMS permissions** - to receive and read SMS messages
   - **Storage permission** - to read the configuration file

## Configuration

Create a configuration file at `/sdcard/Download/sms-forwarder-config.json`:

```json
{
  "bot_token": "YOUR_BOT_TOKEN",
  "chat_id": "YOUR_CHAT_ID"
}
```

**Note:** `/sdcard/Download/` refers to your device's internal storage Download folder, not a physical SD card. This is typically accessible via any file manager under "Download" or "Downloads".

### How to get your bot_token

1. Open Telegram and search for [@BotFather](https://t.me/BotFather)
2. Send `/newbot` command
3. Follow the prompts to name your bot
4. BotFather will give you a token like `123456789:ABCdefGHIjklMNOpqrsTUVwxyz`
5. Copy this token to your config file

### How to get your chat_id

**Option 1: Using @userinfobot**
1. Search for [@userinfobot](https://t.me/userinfobot) on Telegram
2. Start a chat with it
3. It will reply with your user ID - this is your `chat_id`

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

## Permissions Explanation

| Permission | Purpose |
|------------|---------|
| `RECEIVE_SMS` | Detect incoming SMS messages in real-time |
| `READ_SMS` | Read SMS content and check inbox for missed messages |
| `INTERNET` | Send messages to Telegram API |
| `READ_EXTERNAL_STORAGE` | Read configuration file from Download folder |

## Message Format

Forwarded messages appear in Telegram as:

```
📱 Sender: +1234567890
💬 Message: Your SMS content here
```

## Troubleshooting

- **Messages not forwarding:** Check that the config file exists and has valid JSON
- **Bot not responding:** Make sure you've started a conversation with your bot first
- **Permission denied:** Re-open the app and grant all requested permissions
- **Config not found:** Ensure the file is named exactly `sms-forwarder-config.json` in the Download folder

## Building from Source

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`

## License

MIT License
