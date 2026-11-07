# PiSSH

**A modern Android SSH client for managing your Raspberry Pi and remote Linux servers.**

PiSSH is an Android application built with Kotlin and Jetpack Compose. It provides an intuitive interface for connecting to remote Linux systems, managing files and working directly in an SSH terminal.

## Features

- **SSH Connection** – Connect to your Raspberry Pi or any compatible Linux server.
- **Terminal** – Execute commands directly through an interactive SSH shell.
- **Multiple Terminals** – Manage multiple terminal sessions simultaneously using tmux.
- **Persistent Sessions** – Keep terminal sessions running on the remote server when disconnecting.
- **File Manager** – Browse remote directories and manage files.
- **File Upload & Download** – Transfer files between your Android device and remote servers.
- **File Editor** – View and edit remote text files.
- **Modern UI** – Clean, dark interface with red accents.
- **Connection Management** – Reconnect to your SSH server when a connection is interrupted.

## Built With

| Technology        | Purpose                    |
| ----------------- | -------------------------- |
| Kotlin            | Main programming language  |
| Jetpack Compose   | User interface             |
| Material 3        | UI components              |
| JSch              | SSH and SFTP communication |
| Kotlin Coroutines | Asynchronous operations    |
| DataStore         | Local settings storage     |
| Gradle            | Build system               |

## Requirements

- Android 7.0 (API 24) or newer
- Raspberry Pi or Linux server
- SSH server enabled on the remote machine
- Internet or local network connection
- Android Studio for development

**Recommended:** Install `tmux` on your Raspberry Pi to use persistent terminal sessions.

## Instalation

### 1. Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/PiSSH.git
```

### 2. Open the project

1. Open Android Studio.
2. Select **Open**.
3. Choose the cloned `PiSSH` folder.
4. Wait for Gradle synchronization to finish.

### 3. Build the application

On Windows:

```bash
gradlew.bat assembleDebug
```

On Linux/macOS:

```bash
./gradlew assembleDebug
```

The generated APK can be found here:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Raspberry Pi Setup

Enable SSH on your Raspberry Pi.

Using the Raspberry Pi terminal:

```bash
sudo raspi-config
```

Navigate to:

```text
Interface Options → SSH → Enable
```

Install tmux for persistent terminal sessions:

```bash
sudo apt update
sudo apt install tmux
```

Make sure your Raspberry Pi and Android device can communicate over the network.

## Security

- Never publish SSH passwords, private keys or personal credentials.
- Use strong SSH passwords or key-based authentication.
- Verify SSH host keys before using the application in production.
- Avoid exposing SSH directly to the public internet without appropriate security measures.

**Note:** SSH host-key verification should be enabled and properly implemented before using this project in production.

## Project Structure

```text
PiSSH/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/xeuka/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── SshManager.kt
│   │   │   │   ├── TerminalScreen.kt
│   │   │   │   ├── FilesScreen.kt
│   │   │   │   └── SettingsScreen.kt
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   └── test/
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
├── gradlew
├── gradlew.bat
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
└── README.md
```

# Made by Xeuka
