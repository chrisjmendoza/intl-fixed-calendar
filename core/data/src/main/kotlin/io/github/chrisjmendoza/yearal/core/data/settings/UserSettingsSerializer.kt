package io.github.chrisjmendoza.yearal.core.data.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import io.github.chrisjmendoza.yearal.core.domain.settings.ThemeMode
import io.github.chrisjmendoza.yearal.core.domain.settings.UserSettings
import io.github.chrisjmendoza.yearal.core.domain.settings.WeekdayDisplay
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * On-disk shape of [UserSettings]. The domain type stays free of serialization annotations, so this
 * DTO mirrors it field for field. **Every field has a default** so that a file written by an older
 * app version (missing newer fields) still reads; unknown keys from a newer version are ignored by
 * [UserSettingsSerializer.json]. Renaming a field here changes the file format: add `@SerialName`
 * with the old name instead.
 */
@Serializable
internal data class UserSettingsDto(
    val weekdayDisplay: WeekdayDisplay = UserSettings.DEFAULT.weekdayDisplay,
    val themeMode: ThemeMode = UserSettings.DEFAULT.themeMode,
    val dynamicColor: Boolean = UserSettings.DEFAULT.dynamicColor,
    val enabledHolidaySets: Set<String> = UserSettings.DEFAULT.enabledHolidaySets,
) {
    /** This DTO's fields as a [UserSettings]. */
    fun toDomain(): UserSettings =
        UserSettings(
            weekdayDisplay = weekdayDisplay,
            themeMode = themeMode,
            dynamicColor = dynamicColor,
            enabledHolidaySets = enabledHolidaySets,
        )

    companion object {
        /** [settings]'s fields as the DTO shape [UserSettingsSerializer.writeTo] encodes. */
        fun fromDomain(settings: UserSettings): UserSettingsDto =
            UserSettingsDto(
                weekdayDisplay = settings.weekdayDisplay,
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
                enabledHolidaySets = settings.enabledHolidaySets,
            )
    }
}

/**
 * DataStore [Serializer] for [UserSettings] as a UTF-8 JSON document (`docs/ARCHITECTURE.md` §1
 * "datastore": kotlinx-serialization JSON, no protobuf).
 *
 * Compatibility guarantees, both tested in `UserSettingsSerializerTest`:
 * - **Backward:** a document missing a field (written before that field existed) reads with the
 *   [UserSettings.DEFAULT] value for it.
 * - **Forward:** a document with a key this version does not know (written by a newer version) reads
 *   normally; the unknown key is dropped on the next write.
 *
 * Anything that is not a valid document — malformed JSON, an unknown enum constant, a wrong value
 * type, an empty file — is reported as [CorruptionException] and nothing else, so DataStore's
 * `ReplaceFileCorruptionHandler` (wired in `SettingsModule`) can replace the file with the defaults.
 * An empty file counts as corruption rather than "no settings yet": DataStore never calls [readFrom]
 * for a file that does not exist, so a zero-length file means an interrupted or foreign write.
 */
public object UserSettingsSerializer : Serializer<UserSettings> {
    /**
     * `ignoreUnknownKeys` gives forward compatibility; `encodeDefaults` writes every field so the file
     * is self-describing and a later change of a default does not silently alter stored settings.
     */
    internal val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    /** The settings of a fresh install; what DataStore yields while no file exists. */
    override val defaultValue: UserSettings = UserSettings.DEFAULT

    /**
     * Decodes one JSON document from [input].
     *
     * @throws CorruptionException for any input that is not a valid settings document; never any
     * other exception type for bad content.
     */
    override suspend fun readFrom(input: InputStream): UserSettings {
        val text = input.readBytes().decodeToString()
        return try {
            json.decodeFromString(UserSettingsDto.serializer(), text).toDomain()
        } catch (e: SerializationException) {
            throw CorruptionException("User settings file is not a valid settings document", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("User settings file holds an invalid value", e)
        }
    }

    /** Encodes [t] as one JSON document with every field present, including defaults. */
    override suspend fun writeTo(
        t: UserSettings,
        output: OutputStream,
    ) {
        output.write(
            json.encodeToString(UserSettingsDto.serializer(), UserSettingsDto.fromDomain(t)).encodeToByteArray(),
        )
    }
}
