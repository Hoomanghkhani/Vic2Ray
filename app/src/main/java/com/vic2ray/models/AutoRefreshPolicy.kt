package com.vic2ray.models

/** Discovery scheduling is independent of Android lifecycle and networking. */
internal object AutoRefreshPolicy {
    private const val REFRESH_INTERVAL = 60 * 60 * 1000L
    private const val RETRY_INTERVAL = 10 * 60 * 1000L

    fun shouldRefresh(now: Long, lastSync: Long, lastAttempt: Long, enabled: Boolean,
        busy: Boolean, vpnActive: Boolean, hasSources: Boolean,
        hasWorkingServers: Boolean, interactionActive: Boolean = false): Boolean =
        enabled && !busy && !vpnActive && !interactionActive && hasSources &&
            elapsed(now, lastAttempt, RETRY_INTERVAL) &&
            (!hasWorkingServers || elapsed(now, lastSync, REFRESH_INTERVAL))

    private fun elapsed(now: Long, previous: Long, interval: Long): Boolean =
        previous <= 0L || now < previous || now - previous >= interval
}
