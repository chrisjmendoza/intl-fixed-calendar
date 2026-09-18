package io.github.chrisjmendoza.fixedcal.core.testing

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.ZoneId

class FakeZoneProviderTest {
    @Test
    fun `currentZone returns the constructor zone until set changes it`() {
        val provider = FakeZoneProvider(ZoneId.of("America/New_York"))
        provider.currentZone() shouldBe ZoneId.of("America/New_York")

        provider.set(ZoneId.of("Asia/Kathmandu"))
        provider.currentZone() shouldBe ZoneId.of("Asia/Kathmandu")
    }
}
