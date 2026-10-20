# Vic2Ray

[![Android CI and release](https://github.com/Hoomanghkhani/Vic2Ray/actions/workflows/build.yml/badge.svg)](https://github.com/Hoomanghkhani/Vic2Ray/actions/workflows/build.yml)

کلاینت بومی اندروید برای اتصال با VMess، VLESS، Trojan و Shadowsocks، با هستهٔ Xray و رابط فارسی و انگلیسی.

**[دانلود آخرین نسخهٔ اندروید](https://github.com/Hoomanghkhani/Vic2Ray/releases/latest)** · [تغییرات نسخه‌ها](CHANGELOG.md)

## امکانات

- داشبورد تیرهٔ یکپارچه با متن‌های خوانا، کنترل اتصال متحرک، زمان اتصال و تأخیر واقعی پروکسی.
- اتصال سریع به کم‌تأخیرترین سرور تست‌شده، جست‌وجو، فیلتر پروتکل، مرتب‌سازی و علاقه‌مندی‌های ماندگار.
- ورود کانفیگ از متن، کلیپ‌بورد یا فایل و اشتراک‌گذاری هر سرور با برنامه‌های اندروید.
- مدیریت منابع HTTPS، نمایش خطای منابع و حفظ سرورهای ذخیره‌شده هنگام به‌روزرسانی یا قطع اینترنت.
- دریافت خودکار از ۶ منبع GitHub هنگام باز بودن برنامه؛ بررسی ساعتی تا ۶۰ سرور، با امکان خاموش کردن در مدیریت منابع. هنگام اتصال VPN و در پس‌زمینه متوقف می‌شود.
- تست هم‌زمان سرورها با پیشرفت دقیق و امکان توقف؛ دریافت دستی حداکثر ۳۰۰ گزینه را بررسی می‌کند. سهم منابع به‌نوبت انتخاب می‌شود، کانفیگ‌های واردشده و علاقه‌مندی‌ها اولویت دارند و بخش باقی‌مانده در همگام‌سازی‌های بعدی می‌چرخد.
- نمایش شبکهٔ فعلی و علت دسته‌بندی‌شدهٔ خطاهای تست؛ نتایج وای‌فای و سیم‌کارت با هم مخلوط نمی‌شوند و تست پروکسی به پاسخ مستقیم DNS عمومی وابسته نیست.
- رابط راست‌به‌چپ فارسی، انتخاب زبان و اعلان فعال VPN با دکمهٔ قطع اتصال.

## نصب و استفاده

فایل `Vic2Ray-VPN-<version>-release.apk` را از Releases دریافت کن. Android 7 یا جدیدتر با معماری `arm64-v8a` یا `x86_64` لازم است؛ گوشی‌های ۳۲ بیتی پشتیبانی نمی‌شوند. فایل `SHA256SUMS` کنار APK برای بررسی صحت دانلود قرار دارد.

برنامه در اولین اجرا منابع را خودکار دریافت و سرورها را آزمایش می‌کند. برای جستجوی بیشتر «به‌روزرسانی و آزمایش» را بزن، یا از منوی ورود کانفیگ معتبر خودت را وارد کن. بعد از تست، با کنترل مرکزی به سریع‌ترین سرور وصل شو یا سرور دلخواه را از فهرست انتخاب کن. مجوز VPN را در پنجرهٔ اندروید تأیید کن. کانفیگ واردشده بدون تست هم از فهرست قابل انتخاب است.

در «مدیریت منابع»، نام منابع، تعداد کانفیگ‌های سازگار دریافت‌شده و وضعیت خطای آخرین دریافت نمایش داده می‌شود. دریافت خودکار فقط در زمان باز بودن برنامه و بدون اتصال فعال VPN انجام می‌شود؛ پس از خطا یا توقف دستی حداقل ۱۰ دقیقه فاصله دارد. دکمهٔ به‌روزرسانی دستی این انتظار را ندارد.

پس از تغییر شبکه، تست قبلی متوقف می‌شود و وضعیت سرورها برای شبکهٔ جدید بررسی می‌شود. نتیجهٔ موفق روی سیم‌کارت تضمین اتصال روی وای‌فای نیست؛ محدودیت دسترسی به سرور، DNS و کیفیت مسیر هر شبکه می‌تواند متفاوت باشد. هنگام بازگشت به شبکه، فقط نتایج کمتر از یک ساعت قبل برای همان شناسهٔ شبکه بازیابی می‌شوند؛ حافظهٔ موقت حداکثر سه شبکه را نگه می‌دارد. پس از بستن کامل برنامه، وضعیت اتصال دوباره آزمایش می‌شود. منابع پیش‌فرض رایگان‌اند و دسترسی همیشگی هیچ سرور عمومی تضمین نمی‌شود.

نسخه‌های قدیمی با کلید دیباگ موقتی امضا شده‌اند؛ برای مهاجرت به اولین نسخه با امضای ثابت ممکن است یک بار حذف و نصب لازم باشد. قبل از حذف، لینک‌های موردنیاز را به اشتراک بگذار و نگه دار؛ حذف برنامه اطلاعات محلی آن را پاک می‌کند.

## منابع پیش‌فرض

این فایل‌های اشتراک در ۲۰ اکتبر ۲۰۲۶ با پاسخ HTTP 200، تغییرات همان روز و پردازشگر واقعی اپ بررسی شدند. تعدادها مربوط به همان بررسی است و با تغییر فایل‌ها عوض می‌شود؛ «سازگار» به معنی عبور از پردازش لینک است، نه تأیید اتصال روی اینترنت کاربر.

| مخزن | فایل اشتراک | لینک سازگار در بررسی |
| --- | --- | ---: |
| [0xRadikal/Free-v2ray-Configs](https://github.com/0xRadikal/Free-v2ray-Configs) | `top100.txt` | ۱۰۰ |
| [MatinGhanbari/v2ray-configs](https://github.com/MatinGhanbari/v2ray-configs) | `subscriptions/v2ray/super-sub.txt` | ۱۸۹ |
| [roosterkid/openproxylist](https://github.com/roosterkid/openproxylist) | `V2RAY_RAW.txt` | ۱۲۵ |
| [mahdibland/V2RayAggregator](https://github.com/mahdibland/V2RayAggregator) | `Eternity.txt` | ۱۸۶ |
| [barry-far/V2ray-Config](https://github.com/barry-far/V2ray-Config) | `Sub1.txt` | ۴۴۶ |
| [peasoft/NoMoreWalls](https://github.com/peasoft/NoMoreWalls) | `list.txt` | ۳۵۷ |

حجم مجموع این فایل‌ها حدود ۳۵۷ کیبی‌بایت بود و ۱۴۰۰ لینک بدون تکرار دقیق داشتند. منبع قدیمی `w1770946466/Auto_proxy` که فایلش از مارس ۲۰۲۴ به‌روز نشده بود، کنار گذاشته شد. هنگام ارتقا، فهرست پیش‌فرض دست‌نخوردهٔ نسخه‌های قبلی خودکار مهاجرت می‌کند؛ منابع شخصی و فهرست عمداً خالی جایگزین نمی‌شوند. برای استفاده از فهرست جدید در یک نصب با منابع شخصی، «بازگردانی منابع پیش‌فرض» در دسترس است.

## Connection behavior and privacy

Subscriptions and proxy servers are operated by third parties. Choose providers you trust; a proxy operator can observe connection metadata. Imported links, favorites and sources are stored in app-private preferences, and Android backup is disabled. No analytics service is included.

The bundled custom Xray core reads Android's actual TUN file descriptor. The app routes IPv4 and IPv6 into the tunnel and excludes its own process to let the core reach proxy endpoints. Existing routing policy sends private/local networks, `.ir` domains and the configured Iranian service keywords directly, and blocks UDP/443 to encourage TCP fallback. This is not an all-traffic kill switch. Android's always-on/lockdown behavior is not claimed or automatically enabled.

A successful HTTPS 204 probe measures the local SOCKS proxy's connectivity, not full device traffic or every application's reachability. A running core/tunnel and a working latency probe are presented separately. Test routing forwards hostnames through the proxy without a public-DNS preflight. VPN DNS uses remote HTTPS resolvers through the selected proxy; other DNS record types use proxied TCP DNS. The proxy endpoint itself must still be reachable on the current network; endpoints expressed as hostnames still depend on the system/network resolver for bootstrap.

TLS certificate verification is enabled. Links explicitly requesting insecure verification are rejected during configuration because the bundled native core no longer accepts that setting. Failure categories describe the stage or Java exception observed by the app; an upstream DNS or TLS error may surface as a generic connection error if the core does not expose it.

Each subscription response is limited to 2 MiB. Text/file imports are limited to 1 MiB and 1,000 configurations per import; the saved catalog holds up to 2,000 links. Unsupported or malformed links are skipped. The Android share sheet exports a selected link; treat shared links as credentials.

## Local build

Use JDK 21, Android SDK platform 35 and Build Tools 35.0.0. The Gradle wrapper is checked in; the bundled `app/libs/libv2ray.aar` is required. Configure `ANDROID_HOME` or an ignored `local.properties` containing `sdk.dir=/path/to/android-sdk`.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check_theme_contrast.py
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
2. A successful push to `main` with an unpublished version automatically builds and publishes a signed release. A matching version tag, such as `v1.3.0`, can also trigger publication. Existing version tags are never moved to new commits, and existing releases are not overwritten.
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

JVM tests cover catalog filtering/sorting, deduplication, source validation, fair interleaving/scan rotation, default-source migration, automatic-refresh eligibility/cooldown, source-document mapping, request cancellation/cleanup, network health isolation/expiry, probe failure categories and parsing/configuration regressions. Delivery tests cover malformed versions, tag mismatches, repeat runs and publication decisions. Android lint and APK builds check integration. Theme color pairs are checked for text contrast; actual device rendering still needs inspection.

A controlled desktop Xray experiment reproduced a healthy loopback proxy timing out when the old routing waited for unavailable direct DNS; forwarding the hostname with `AsIs` succeeded without those DNS queries. This establishes a configuration failure mechanism, not the cause of every Wi-Fi failure. Android network callbacks, DNS over the actual TUN interface and the changed configuration still require device validation.

Real-device checks remain necessary for VPN consent, actual TUN traffic, messaging apps, rapid reconnect/disconnect, background survival, notification controls, Android 7/15 compatibility and screen-reader/large-font/RTL behavior. Desktop builds cannot establish an Android VPN session.

The repository contains a bundled native core; its upstream licensing and provenance need to be retained when redistributing it. This repository currently does not declare a project-wide license file.
