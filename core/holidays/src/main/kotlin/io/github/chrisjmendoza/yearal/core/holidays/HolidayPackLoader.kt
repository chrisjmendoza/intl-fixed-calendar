package io.github.chrisjmendoza.yearal.core.holidays

import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidaySet
import io.github.chrisjmendoza.yearal.core.holidays.internal.PackDto
import io.github.chrisjmendoza.yearal.core.holidays.internal.PackMapper
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Reads holiday packs — JSON files in the schema-1 format of `docs/holidays-and-import.md` §2.4 — into
 * [HolidaySet]s for the engine in `:core:domain`.
 *
 * Parsing is strict: every key must be known (a typo in a pack is a test failure, not a silently
 * ignored field), every rule must have a `"type"` among `fixed`, `nthWeekday`, `weekdayRelative`,
 * `offset`, `easter`, `table` and `ifc`, and every field the domain model validates (month and day
 * ranges, `since <= until`, the `"en"` name, unique ids, …) is validated on load. **The spec's
 * `calendar` rule type is not supported by schema 1** — a pack containing it fails to load with a
 * message saying so (FEATURES H4); lunisolar holidays ship as inline `table` rules. Likewise a
 * `table` rule holds its dates inline (`"dates": {"2024": "2024-11-01"}`); the spec example's
 * reference to a separate file under `tables/` is a later extension.
 *
 * A pack loads whole or not at all. Loading is pure and stateless; the result is a value that can be
 * cached by the caller. Instances are thread-safe.
 *
 * Spec: `docs/holidays-and-import.md` §2.4, §2.5, §5; `docs/adr/0004-holiday-pack-format.md`.
 */
public class HolidayPackLoader {
    private val json: Json =
        Json {
            ignoreUnknownKeys = false
            isLenient = false
        }

    /**
     * Parses [text] as a schema-1 pack and returns the resulting set.
     *
     * @param packName how the pack is referred to in error messages; for bundled packs this is the
     *   file name without extension (`"US"`), for anything else whatever identifies the source.
     * @param text the complete JSON document.
     * @throws HolidayPackException if [text] is not valid JSON, is not a schema-1 pack, contains an
     *   unknown key or rule type, or describes a holiday the domain model rejects. The exception names
     *   [packName] and, where the failure lies inside a holiday entry, that holiday's id.
     */
    public fun load(
        packName: String,
        text: String,
    ): HolidaySet {
        val dto =
            try {
                json.decodeFromString(PackDto.serializer(), text)
            } catch (e: SerializationException) {
                throw HolidayPackException(packName, null, e.message ?: "malformed pack", e)
            } catch (e: IllegalArgumentException) {
                // kotlinx.serialization reports some structural problems (a non-object root, an
                // unexpected token) as IllegalArgumentException rather than SerializationException.
                throw HolidayPackException(packName, null, e.message ?: "malformed pack", e)
            }
        return PackMapper.toHolidaySet(packName, dto)
    }

    /**
     * Loads the pack shipped in this module's resources as `/holidays/<packName>.json`, e.g.
     * [BundledHolidayPacks.US]. The file is read as UTF-8.
     *
     * @throws HolidayPackException if no bundled pack has that name, or if it fails to load as
     *   described on [load] (a bundled pack that fails is a packaging bug; the tests load every one).
     */
    public fun loadBundled(packName: String): HolidaySet {
        val path = "$RESOURCE_DIRECTORY/$packName.json"
        val stream =
            HolidayPackLoader::class.java.getResourceAsStream(path)
                ?: throw HolidayPackException(packName, null, "no bundled pack at classpath resource $path")
        val text = stream.use { it.readBytes().toString(Charsets.UTF_8) }
        return load(packName, text)
    }

    private companion object {
        /** Classpath directory holding the bundled packs. */
        const val RESOURCE_DIRECTORY = "/holidays"
    }
}
