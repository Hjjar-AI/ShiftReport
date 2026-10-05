# ShiftReport

The APK is hospital-agnostic. On first launch, choose one of these paths:

- **Create project:** create a Telegram bot through BotFather, add it as an administrator to a new supergroup with permission to send files and pin messages, then enter the hospital name, token, group ID, optional topic IDs, and first administrator identity.
- **Join project:** obtain the same connection values from the project administrator. The app validates the bot and group, then downloads the pinned doctors registry.

The bot token and project identifiers are stored in encrypted Android preferences. They are not compiled into the APK, stored in Room, or included in application backups. Topic IDs are optional; an empty topic posts to General.

The existing application logo and About page remain the permanent product identity and credits page. The configured hospital name is shown before clinician selection and in the clinical ward header.

## Developer commands

```bash
# Build the debug APK
./gradlew assembleDebug

# Build the release APK
./gradlew assembleRelease

# Build the release Android App Bundle (AAB)
./gradlew bundleRelease

# Compile Kotlin without packaging an APK
./gradlew :app:compileDebugKotlin

# Install the debug APK on a connected device
./gradlew installDebug

# Clean generated build files
./gradlew clean

# List all available Gradle tasks
./gradlew tasks

# Build the debug APK with detailed error output
./gradlew assembleDebug --stacktrace

# Stop active Gradle daemons
./gradlew --stop
```

Generated files are normally located under:

```text
app/build/outputs/apk/debug/
app/build/outputs/apk/release/
app/build/outputs/bundle/release/
```
