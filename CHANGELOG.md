# Changelog

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
