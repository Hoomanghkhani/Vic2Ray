package com.vic2ray.models

enum class NetworkType { WIFI, MOBILE, OTHER, OFFLINE }

/** Health belongs to a physical network; catalog identity and preferences do not. */
internal class NetworkHealthCache {
    private data class Health(val measuredAt: Long, val ping: Int, val working: Boolean,
        val failure: TestFailure?, val httpStatus: Int?)

    private val snapshots = linkedMapOf<Long, MutableMap<String, Health>>()
    private var activeNetwork: Long? = null

    fun switchTo(networkHandle: Long?, configs: List<VpnConfig>, now: Long): List<VpnConfig> {
        if (networkHandle != null && networkHandle == activeNetwork) return configs
        snapshots.values.forEach { removeExpired(it, now) }
        activeNetwork = networkHandle
        val saved = networkHandle?.let { snapshots.remove(it) ?: linkedMapOf() }
        val restored = configs.map { config ->
            val health = saved?.get(config.rawConfig)
            config.copy(ping = health?.ping ?: -1, isWorking = health?.working ?: false,
                lastTestFailure = health?.failure, lastHttpStatus = health?.httpStatus)
        }
        if (networkHandle != null && saved != null) snapshots[networkHandle] = saved
        while (snapshots.size > MAX_NETWORKS) snapshots.remove(snapshots.keys.first())
        return restored
    }

    fun record(result: VpnConfig, now: Long) {
        activeNetwork?.let { network ->
            val measurements = snapshots.getValue(network)
            removeExpired(measurements, now)
            measurements[result.rawConfig] = Health(now, result.ping, result.isWorking,
                result.lastTestFailure, result.lastHttpStatus)
            while (measurements.size > MAX_LINKS) {
                val oldest = measurements.minByOrNull { it.value.measuredAt }?.key ?: break
                measurements.remove(oldest)
            }
        }
    }

    private fun removeExpired(measurements: MutableMap<String, Health>, now: Long) {
        measurements.entries.removeAll { now < it.value.measuredAt || now - it.value.measuredAt >= MAX_AGE }
    }

    private companion object {
        const val MAX_NETWORKS = 3
        const val MAX_LINKS = 2_000
        const val MAX_AGE = 60 * 60 * 1000L
    }
}
