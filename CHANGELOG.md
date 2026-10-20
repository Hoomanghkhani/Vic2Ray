# Changelog

## 1.2.0 — 2026-10-20

- Selected six compact GitHub subscriptions using live downloads, file-level update dates and the app's actual parser; replaced the stale Auto_proxy feed and updated the aggregator's canonical repository path.
- Added default-enabled discovery on foreground startup and hourly refresh, with a 60-candidate automatic scan, ten-minute retry/stop cooldown and an off switch in subscription settings. Active VPN sessions and backgrounding pause automatic discovery; connection requests cancel scanning.
- Added friendly source names and received-configuration counts. Sources with no supported links now report an unavailable/invalid result rather than a silent success.
- Interleaved unique links from each source before the bounded scan so large or duplicate-heavy feeds cannot crowd out smaller sources. Manual discovery still scans up to 300 candidates and subsequent scans rotate through the queue.
- Retained scan progress after partial cancellation and paused automatic discovery while importing personal configurations, so brief visits and manual imports remain useful.
- Migrated untouched old default subscriptions while retaining customized or deliberately empty lists and all saved/imported servers and favorites.
- Fixed black text on the dark dashboard by supplying the root content color. Reworked the palette around navy/slate, cyan and mint, with explicit text, field, menu, chip, dialog, snackbar and disabled-control colors.
- Added a CI contrast check over 45 opaque color pairs to reject unreadable text/control combinations.
- Added regression coverage for scheduling, cooldowns, source migrations, fair scanning, URL/document mapping and fetch cancellation. Version 1.2.0 / Android code 23 keeps the stable release signing key introduced in 1.1.0.

## 1.1.0 — 2026-10-20

- Rebuilt the native dashboard with animated connection controls, progress feedback, Persian/English language switching and RTL support.
- Added fastest tested server connection, search, protocol filters, latency/name sorting, persistent favorites and per-server sharing.
- Added bounded text/file/clipboard configuration imports that survive subscription refreshes.
- Kept saved servers usable during refresh, cancellation and source failures. Added last-sync information, source error reporting and HTTPS source validation.
- Removed two unavailable default collectors and corrected Barry's subscription repository URL. Existing untouched default lists migrate automatically; customized source lists are preserved.
- Limited scans to 300 candidates per refresh, prioritizing imported/favorite links and rotating through other candidates on later scans. Saved catalog is bounded to 2,000 links and each import to 1,000 new links.
- Serialized VPN lifecycle work, added an ongoing notification with disconnect action, validated tunnel/core startup and prevented stale ping results after disconnect.
- Enabled TLS certificate verification by default. Improved Shadowsocks credential/IPv6 handling, Base64 subscriptions, malformed-link rejection and deduplication.
- Used isolated test inbound ports and HTTPS probes that require HTTP 204, with reliable request cleanup and cancellation.
- Disabled Android backup of stored proxy configurations and subscriptions.
- Centralized version 1.1.0 / Android code 22, targeted Android 15/API 35 and aligned local/CI builds on JDK 21.
- Added pull-request/main/manual checks and automatic stable-key signed GitHub releases with APK metadata/signature verification and SHA-256 checksums.

### Installation note

Earlier releases used runner-generated debug signing keys. The first installation of the production-signed app may require removing the old app. Export any configurations you need first; uninstalling removes app-local data. Later production releases must use the same release key.
