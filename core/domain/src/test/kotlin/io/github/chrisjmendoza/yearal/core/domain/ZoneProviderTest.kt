package io.github.chrisjmendoza.yearal.core.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.ZoneId

class ZoneProviderTest {
    @Test
    fun `SystemZoneProvider returns the JVM's current default zone`() {
        SystemZoneProvider.currentZone() shouldBe ZoneId.systemDefault()
    }

    @Test
    fun `ZoneProvider is a functional interface usable as a lambda`() {
        val fixed = ZoneId.of("Pacific/Kiritimati")
        val provider = ZoneProvider { fixed }
        provider.currentZone() shouldBe fixed
    }
}
