package com.vic2ray.models

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionSourcesTest {
    @Test fun `fresh install receives the curated subscriptions`() {
        assertEquals(SubscriptionSources.urls, SubscriptionSources.migrate(null))
    }

    @Test fun `untouched version 1_1 defaults migrate away from stale sources`() {
        val previous = listOf(
            "https://raw.githubusercontent.com/mahdibland/ShadowsocksAggregator/master/Eternity",
            "https://raw.githubusercontent.com/w1770946466/Auto_proxy/main/Long_term_subscription_num",
            "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub1.txt"
        )
        assertEquals(SubscriptionSources.urls, SubscriptionSources.migrate(previous))
    }

    @Test fun `original untouched defaults also migrate`() {
        val original = listOf(
            "https://raw.githubusercontent.com/yebekhe/TelegramV2rayCollector/main/sub/normal/mix",
            "https://raw.githubusercontent.com/mahdibland/ShadowsocksAggregator/master/Eternity",
            "https://raw.githubusercontent.com/w1770946466/Auto_proxy/main/Long_term_subscription_num",
            "https://raw.githubusercontent.com/soroushmirzaei/telegram-configs-collector/main/channels/protocols/vless",
            "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Sub1.txt"
        )
        assertEquals(SubscriptionSources.urls, SubscriptionSources.migrate(original))
    }

    @Test fun `custom subscriptions are normalized without replacing user choices`() {
        val saved = listOf(" https://example.org/mine?token=private ",
            "http://invalid.org/sub", "https://example.org/mine?token=private")
        assertEquals(listOf("https://example.org/mine?token=private"),
            SubscriptionSources.migrate(saved))
    }

    @Test fun `intentional empty subscriptions remain empty`() {
        assertEquals(emptyList<String>(), SubscriptionSources.migrate(emptyList()))
    }

    @Test fun `adding one custom source prevents automatic replacement of old defaults`() {
        val saved = listOf(
            "https://raw.githubusercontent.com/mahdibland/ShadowsocksAggregator/master/Eternity",
            "https://raw.githubusercontent.com/w1770946466/Auto_proxy/main/Long_term_subscription_num",
            "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub1.txt",
            "https://example.org/custom"
        )
        assertEquals(saved, SubscriptionSources.migrate(saved))
    }
}
