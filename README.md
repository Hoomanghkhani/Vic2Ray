# Vic2Ray

[![Android CI and release](https://github.com/Hoomanghkhani/Vic2Ray/actions/workflows/build.yml/badge.svg)](https://github.com/Hoomanghkhani/Vic2Ray/actions/workflows/build.yml)

کلاینت بومی اندروید برای اتصال با VMess، VLESS، Trojan و Shadowsocks، با هستهٔ Xray و رابط فارسی و انگلیسی.

**[دانلود آخرین نسخهٔ اندروید](https://github.com/Hoomanghkhani/Vic2Ray/releases/latest)** · [تغییرات نسخه‌ها](CHANGELOG.md)

## امکانات

- داشبورد تیره با کنترل اتصال متحرک، زمان اتصال و تأخیر واقعی پروکسی.
- اتصال سریع به کم‌تأخیرترین سرور تست‌شده، جست‌وجو، فیلتر پروتکل، مرتب‌سازی و علاقه‌مندی‌های ماندگار.
- ورود کانفیگ از متن، کلیپ‌بورد یا فایل و اشتراک‌گذاری هر سرور با برنامه‌های اندروید.
- مدیریت منابع HTTPS، نمایش خطای منابع و حفظ سرورهای ذخیره‌شده هنگام به‌روزرسانی یا قطع اینترنت.
- تست هم‌زمان سرورها با پیشرفت دقیق و امکان توقف؛ هر بار حداکثر ۳۰۰ گزینه بررسی می‌شود. کانفیگ‌های واردشده و علاقه‌مندی‌ها اولویت دارند و بخش باقی‌مانده در همگام‌سازی‌های بعدی می‌چرخد.
- رابط راست‌به‌چپ فارسی، انتخاب زبان و اعلان فعال VPN با دکمهٔ قطع اتصال.

## نصب و استفاده

فایل `Vic2Ray-VPN-<version>-release.apk` را از Releases دریافت کن. Android 7 یا جدیدتر با معماری `arm64-v8a` یا `x86_64` لازم است؛ گوشی‌های ۳۲ بیتی پشتیبانی نمی‌شوند. فایل `SHA256SUMS` کنار APK برای بررسی صحت دانلود قرار دارد.

در برنامه «یافتن سرورها» را بزن یا از منوی ورود، کانفیگ معتبر خودت را وارد کن. بعد از تست، با کنترل مرکزی به سریع‌ترین سرور وصل شو یا سرور دلخواه را از فهرست انتخاب کن. مجوز VPN را در پنجرهٔ اندروید تأیید کن. کانفیگ واردشده بدون تست هم از فهرست قابل انتخاب است.

نسخه‌های قدیمی با کلید دیباگ موقتی امضا شده‌اند؛ برای مهاجرت به اولین نسخه با امضای ثابت ممکن است یک بار حذف و نصب لازم باشد. قبل از حذف، لینک‌های موردنیاز را به اشتراک بگذار و نگه دار؛ حذف برنامه اطلاعات محلی آن را پاک می‌کند.

## Connection behavior and privacy

Subscriptions and proxy servers are operated by third parties. Choose providers you trust; a proxy operator can observe connection metadata. Imported links, favorites and sources are stored in app-private preferences, and Android backup is disabled. No analytics service is included.

The bundled custom Xray core reads Android's actual TUN file descriptor. The app routes IPv4 and IPv6 into the tunnel and excludes its own process to let the core reach proxy endpoints. Existing routing policy sends private/local networks, `.ir` domains and the configured Iranian service keywords directly, and blocks UDP/443 to encourage TCP fallback. This is not an all-traffic kill switch. Android's always-on/lockdown behavior is not claimed or automatically enabled.

A successful HTTPS 204 probe measures the local SOCKS proxy's connectivity, not full device traffic or every application's reachability. A running core/tunnel and a working latency probe are presented separately. TLS certificate verification is enabled unless the imported link explicitly requests insecure verification.

Each subscription response is limited to 2 MiB. Text/file imports are limited to 1 MiB and 1,000 configurations per import; the saved catalog holds up to 2,000 links. Unsupported or malformed links are skipped. The Android share sheet exports a selected link; treat shared links as credentials.

## Local build

Use JDK 21, Android SDK platform 35 and Build Tools 35.0.0. The Gradle wrapper is checked in; the bundled `app/libs/libv2ray.aar` is required. Configure `ANDROID_HOME` or an ignored `local.properties` containing `sdk.dir=/path/to/android-sdk`.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
python3 -m unittest discover -s scripts/tests -v
```

The debug APK is written to `app/build/outputs/apk/debug/Vic2Ray-VPN-<version>-debug.apk`. To build a production APK, set these environment variables in a private shell or your local secret manager:

| Variable | Value |
| --- | --- |
| `ANDROID_KEYSTORE_PATH` | Absolute path to the persistent release keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing key alias |
| `ANDROID_KEY_PASSWORD` | Signing key password |

```bash
./gradlew assembleRelease
bash scripts/package_apk.sh release
```

Release assembly fails if signing credentials are incomplete. Packaged output and `SHA256SUMS` are written to `dist/release/`. Keep the same signing key for all future updates. Local `.signing/` storage is ignored by Git; back it up privately.

## Automated builds and releases

[`version.properties`](version.properties) is the single source of truth for the displayed version, Android version code, APK name and release tag. Increase both values for a new release.

1. Pull requests, pushes to `main`, version tags and manual runs execute release-rule tests, Android unit tests, lint and debug assembly. Debug APK/checksum and verification reports are saved as workflow artifacts.
2. A successful push to `main` with an unpublished version automatically builds and publishes a signed release. A matching version tag, such as `v1.1.0`, can also trigger publication. Existing version tags are never moved to new commits, and existing releases are not overwritten.
3. The release job verifies the APK signature, application ID, version name/code and checksum before uploading the APK and `SHA256SUMS` to GitHub Releases. Manual runs validate and build without publishing.

Configure these repository Actions secrets before the first release:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 of the persistent release keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing key alias |
| `ANDROID_KEY_PASSWORD` | Signing key password |

The workflow uses its own `GITHUB_TOKEN` with write access restricted to publication; no personal token is needed in repository secrets. Tag creation and release upload happen in the same job, so publication does not rely on a token-created tag starting another workflow.

## Verification scope

JVM tests cover catalog filtering/sorting, deduplication, source validation, scan rotation and parsing/configuration regressions. Delivery tests cover malformed versions, tag mismatches, repeat runs and publication decisions. Android lint and APK builds check integration.

Real-device checks remain necessary for VPN consent, actual TUN traffic, messaging apps, rapid reconnect/disconnect, background survival, notification controls, Android 7/15 compatibility and screen-reader/large-font/RTL behavior. Desktop builds cannot establish an Android VPN session.

The repository contains a bundled native core; its upstream licensing and provenance need to be retained when redistributing it. This repository currently does not declare a project-wide license file.
