package com.vic2ray.models

data class SubscriptionSource(val name: String, val url: String)

/** Compact public feeds checked for freshness and parser compatibility on 2026-10-20. */
object SubscriptionSources {
    val defaults = listOf(
        SubscriptionSource("0xRadikal / Top 100",
            "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/top100.txt"),
        SubscriptionSource("MatinGhanbari / Super subscription",
            "https://raw.githubusercontent.com/MatinGhanbari/v2ray-configs/main/subscriptions/v2ray/super-sub.txt"),
        SubscriptionSource("roosterkid / Openproxylist",
            "https://raw.githubusercontent.com/roosterkid/openproxylist/main/V2RAY_RAW.txt"),
        SubscriptionSource("mahdibland / V2RayAggregator",
            "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/Eternity.txt"),
        SubscriptionSource("barry-far / V2ray-config",
            "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub1.txt"),
        SubscriptionSource("peasoft / NoMoreWalls",
            "https://raw.githubusercontent.com/peasoft/NoMoreWalls/master/list.txt")
    )
    val urls = defaults.map { it.url }

    /** An intentionally empty or customized list must survive an application update. */
    fun migrate(saved: List<String>?): List<String> {
        if (saved == null) return urls
        val normalized = saved.map(String::trim).filter(ServerCatalog::isValidSource).distinct()
        return if (normalized == previousDefaults || normalized == originalDefaults) urls else normalized
    }

    fun displayName(url: String): String? = defaults.firstOrNull { it.url == url }?.name

    private val previousDefaults = listOf(
        "https://raw.githubusercontent.com/mahdibland/ShadowsocksAggregator/master/Eternity",
        "https://raw.githubusercontent.com/w1770946466/Auto_proxy/main/Long_term_subscription_num",
        "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub1.txt"
    )
    private val originalDefaults = listOf(
        "https://raw.githubusercontent.com/yebekhe/TelegramV2rayCollector/main/sub/normal/mix",
        previousDefaults[0], previousDefaults[1],
        "https://raw.githubusercontent.com/soroushmirzaei/telegram-configs-collector/main/channels/protocols/vless",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Sub1.txt"
    )
}
