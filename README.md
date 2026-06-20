# Vic2Ray VPN 🚀

Vic2Ray VPN is a modern, lightweight, and fast V2Ray client for Android. It supports parsing and connecting to raw configuration text lists from multiple open-source repositories.

## ✨ Features

- **Multi-Protocol Support:** Connect to `VMESS`, `VLESS`, `TROJAN`, and `Shadowsocks (SS)` protocols seamlessly.
- **Auto Ping & Test:** Tests all servers using true TCP socket streaming for accurate latency calculation.
- **Smart Source Management:** Easily add or remove raw server list URLs directly from the app interface.
- **Pull-to-Refresh:** Smooth UI updates with drag-to-refresh to instantly sync new servers.
- **Modern Jetpack Compose UI:** A beautiful, responsive, and dark-themed UI built natively for Android.
- **No Background Crashes:** Fully optimized `VpnService` with zero foreground crash issues across Android 8 to 14+.

## 📥 Installation

You can download the latest APK from the [Releases](../../releases/latest) section.
If the repository has GitHub Actions enabled, every new version tag triggers an automatic build.

## 🛠️ Build it Yourself

To build the project locally using Android Studio or the command line:

```bash
# Clone the repository
git clone https://github.com/Hoomanghkhani/Vic2Ray.git

# Enter the directory
cd Vic2Ray

# Build using Gradle
./gradlew assembleDebug
```
*(The compiled APK will be located in `app/build/outputs/apk/debug/`)*

## 🧩 Built With

- [Kotlin](https://kotlinlang.org/) - Programming language
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - UI toolkit
- [Libv2ray](https://github.com/v2fly/v2ray-core) - Core engine running the VPN tunnel
- `Coroutines` and `Flow` for asynchronous operations.

## 📝 License

This project is intended for educational purposes and open-source contributions. 
Enjoy an uncensored internet experience! 🌍
