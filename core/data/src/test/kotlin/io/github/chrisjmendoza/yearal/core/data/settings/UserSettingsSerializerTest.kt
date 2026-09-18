package io.github.chrisjmendoza.yearal.core.data.settings

import androidx.datastore.core.CorruptionException
import io.github.chrisjmendoza.yearal.core.domain.settings.ThemeMode
import io.github.chrisjmendoza.yearal.core.domain.settings.UserSettings
import io.github.chrisjmendoza.yearal.core.domain.settings.WeekdayDisplay
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Plain-JVM tests for [UserSettingsSerializer]: round trips, the compatibility guarantees in its KDoc,
 * and that every kind of bad input surfaces as [CorruptionException] and nothing else
 * (`docs/ARCHITECTURE.md` §6, ":core:data — DataStore serializer round trip and corruption fallback").
 */
class UserSettingsSerializerTest {
    private val nonDefault =
        UserSettings(
            weekdayDisplay = WeekdayDisplay.ACTUAL,
            themeMode = ThemeMode.DARK,
            dynamicColor = false,
            enabledHolidaySets = setOf("ifc", "us", "gb"),
        )

    private suspend fun write(settings: UserSettings): ByteArray =
        ByteArrayOutputStream().also { UserSettingsSerializer.writeTo(settings, it) }.toByteArray()

    private suspend fun read(text: String): UserSettings =
        UserSettingsSerializer.readFrom(ByteArrayInputStream(text.encodeToByteArray()))

    @Test
    fun `default value is the fresh-install settings`() {
        UserSettingsSerializer.defaultValue shouldBe UserSettings.DEFAULT
    }

    @Test
    fun `a non-default value round-trips exactly`() =
        runTest {
            val bytes = write(nonDefault)
            UserSettingsSerializer.readFrom(ByteArrayInputStream(bytes)) shouldBe nonDefault
        }

    @Test
    fun `the defaults round-trip and every field is written explicitly`() =
        runTest {
            val bytes = write(UserSettings.DEFAULT)
            UserSettingsSerializer.readFrom(ByteArrayInputStream(bytes)) shouldBe UserSettings.DEFAULT
            val text = bytes.decodeToString()
            listOf("weekdayDisplay", "themeMode", "dynamicColor", "enabledHolidaySets").forEach {
                text shouldContain
                    "\"$it\""
            }
        }

    @Test
    fun `holiday set order is preserved through the file`() =
        runTest {
            val ordered = UserSettings(enabledHolidaySets = setOf("zz", "aa", "mm"))
            UserSettingsSerializer
                .readFrom(
                    ByteArrayInputStream(write(ordered)),
                ).enabledHolidaySets
                .toList() shouldContainExactly
                listOf("zz", "aa", "mm")
        }

    @Test
    fun `forward compatibility - an unknown key from a newer version is ignored`() =
        runTest {
            val text =
                """{"weekdayDisplay":"NOMINAL","themeMode":"LIGHT","dynamicColor":true,""" +
                    """"enabledHolidaySets":["ifc"],"futureField":{"nested":1}}"""
            read(text) shouldBe
                UserSettings(
                    weekdayDisplay = WeekdayDisplay.NOMINAL,
                    themeMode = ThemeMode.LIGHT,
                    dynamicColor = true,
                    enabledHolidaySets = setOf("ifc"),
                )
        }

    @Test
    fun `backward compatibility - a missing field takes its default`() =
        runTest {
            read("""{"themeMode":"DARK"}""") shouldBe UserSettings.DEFAULT.copy(themeMode = ThemeMode.DARK)
            read("{}") shouldBe UserSettings.DEFAULT
        }

    @Test
    fun `malformed JSON is reported as corruption`() =
        runTest {
            shouldThrow<CorruptionException> { read("{not json") }
        }

    @Test
    fun `an unknown enum constant is reported as corruption`() =
        runTest {
            shouldThrow<CorruptionException> { read("""{"themeMode":"SEPIA"}""") }
        }

    @Test
    fun `a wrongly typed field is reported as corruption`() =
        runTest {
            shouldThrow<CorruptionException> { read("""{"dynamicColor":"yes"}""") }
            shouldThrow<CorruptionException> { read("""{"enabledHolidaySets":"ifc"}""") }
        }

    @Test
    fun `an empty file is reported as corruption`() =
        runTest {
            shouldThrow<CorruptionException> { read("") }
            shouldThrow<CorruptionException> { read("   \n") }
        }

    @Test
    fun `binary garbage is reported as corruption`() =
        runTest {
            val garbage = ByteArray(64) { (it * 37 + 11).toByte() }
            shouldThrow<CorruptionException> { UserSettingsSerializer.readFrom(ByteArrayInputStream(garbage)) }
        }
}
