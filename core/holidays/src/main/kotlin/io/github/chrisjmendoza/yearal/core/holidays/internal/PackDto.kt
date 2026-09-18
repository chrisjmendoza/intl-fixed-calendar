package io.github.chrisjmendoza.yearal.core.holidays.internal

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

// The wire format of a holiday pack, schema 1 (docs/holidays-and-import.md §2.4, docs/adr/0004).
// These types exist only to be decoded by kotlinx.serialization; PackMapper turns them into the
// :core:domain model, which does the semantic validation. Keeping the two apart means the JSON shape
// can evolve (schema 2, file-referenced tables, the `calendar` rule) without touching the engine.

/** The whole file. `schema` is checked by the mapper so a future schema fails with a clear message. */
@Serializable
internal data class PackDto(
    val schema: Int,
    val id: String,
    val region: String? = null,
    val name: Map<String, String>,
    val sources: List<String> = emptyList(),
    val holidays: List<HolidayDto>,
)

/** One holiday entry. Defaults mirror `HolidayDefinition`'s. */
@Serializable
internal data class HolidayDto(
    val id: String,
    val name: Map<String, String>,
    val rule: RuleDto,
    val since: Int? = null,
    val until: Int? = null,
    val yearFilter: YearFilterDto? = null,
    val observed: ObservedDto = ObservedDto.NONE,
    val durationDays: Int = 1,
    val startsEveBefore: Boolean = false,
    val approximate: Boolean = false,
    val category: CategoryDto,
)

@Serializable
internal data class YearFilterDto(
    val mod: Int,
    val eq: Int,
)

@Serializable
internal enum class ObservedDto {
    @SerialName("none")
    NONE,

    @SerialName("us_federal")
    US_FEDERAL,

    @SerialName("next_monday")
    NEXT_MONDAY,

    @SerialName("sunday_to_monday")
    SUNDAY_TO_MONDAY,
}

@Serializable
internal enum class CategoryDto {
    @SerialName("public")
    PUBLIC,

    @SerialName("bank")
    BANK,

    @SerialName("observance")
    OBSERVANCE,

    @SerialName("religious")
    RELIGIOUS,

    @SerialName("ifc")
    IFC,
}

/** Three-letter upper-case weekday codes, the only spelling the schema accepts. */
@Serializable
internal enum class WeekdayDto {
    MON,
    TUE,
    WED,
    THU,
    FRI,
    SAT,
    SUN,
}

@Serializable
internal enum class DirectionDto {
    @SerialName("onOrAfter")
    ON_OR_AFTER,

    @SerialName("onOrBefore")
    ON_OR_BEFORE,
}

@Serializable
internal enum class EasterCalendarDto {
    @SerialName("western")
    WESTERN,

    @SerialName("orthodox")
    ORTHODOX,
}

@Serializable
internal enum class IfcSpecialDto {
    YEAR_DAY,
    LEAP_DAY,
}

/**
 * A rule object, discriminated by its `"type"` key (see [RuleDtoSerializer]). Field names and ranges
 * follow the matching `HolidayRule` case; the mapper does the range checks through the domain
 * constructors.
 */
@Serializable(with = RuleDtoSerializer::class)
internal sealed interface RuleDto {
    @Serializable
    data class Fixed(
        val month: Int,
        val day: Int,
    ) : RuleDto

    @Serializable
    data class NthWeekday(
        val month: Int,
        val weekday: WeekdayDto,
        val n: Int,
    ) : RuleDto

    @Serializable
    data class WeekdayRelative(
        val weekday: WeekdayDto,
        val month: Int,
        val day: Int,
        val direction: DirectionDto,
    ) : RuleDto

    @Serializable
    data class Offset(
        val days: Int,
        val base: RuleDto,
    ) : RuleDto

    @Serializable
    data class Easter(
        val calendar: EasterCalendarDto,
        val offset: Int = 0,
    ) : RuleDto

    /** `dates` is year → ISO-8601 date, both as JSON strings (`"2024": "2024-11-01"`). */
    @Serializable
    data class Table(
        val dates: Map<String, String>,
    ) : RuleDto

    /** Either `month` + `day`, or `special`; the mapper rejects any other combination. */
    @Serializable
    data class Ifc(
        val month: Int? = null,
        val day: Int? = null,
        val special: IfcSpecialDto? = null,
    ) : RuleDto
}

/**
 * Decodes a rule object by reading its `"type"` key, removing it, and delegating to the matching
 * [RuleDto] case's generated serializer; encodes by doing the reverse. Written by hand rather than
 * with `classDiscriminator` so that the two unsupported spellings — the spec's `calendar` type, which
 * is deferred to FEATURES H4, and anything unknown — fail with a message that says so, and so that
 * the discriminator never leaks into the DTOs as a field.
 */
internal object RuleDtoSerializer : KSerializer<RuleDto> {
    private const val DISCRIMINATOR = "type"

    private val serializersByType: Map<String, KSerializer<out RuleDto>> =
        linkedMapOf(
            "fixed" to RuleDto.Fixed.serializer(),
            "nthWeekday" to RuleDto.NthWeekday.serializer(),
            "weekdayRelative" to RuleDto.WeekdayRelative.serializer(),
            "offset" to RuleDto.Offset.serializer(),
            "easter" to RuleDto.Easter.serializer(),
            "table" to RuleDto.Table.serializer(),
            "ifc" to RuleDto.Ifc.serializer(),
        )

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("HolidayRule")

    override fun deserialize(decoder: Decoder): RuleDto {
        val input =
            decoder as? JsonDecoder ?: throw SerializationException("Holiday rules can only be decoded from JSON")
        val element = input.decodeJsonElement()
        val obj =
            element as? JsonObject ?: throw SerializationException("A holiday rule must be a JSON object, got $element")
        val typeElement =
            obj[DISCRIMINATOR] ?: throw SerializationException("A holiday rule must have a \"$DISCRIMINATOR\" key")
        val type =
            (typeElement as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: throw SerializationException("Rule \"$DISCRIMINATOR\" must be a string, got $typeElement")
        val serializer =
            serializersByType[type]
                ?: if (type == "calendar") {
                    throw SerializationException(
                        "Rule type \"calendar\" is not supported by pack schema 1; " +
                            "lunisolar holidays ship as \"table\" rules (FEATURES H4)",
                    )
                } else {
                    throw SerializationException(
                        "Unknown rule type \"$type\"; expected one of ${serializersByType.keys}",
                    )
                }
        val withoutType = JsonObject(obj.filterKeys { it != DISCRIMINATOR })
        return input.json.decodeFromJsonElement(serializer, withoutType)
    }

    override fun serialize(
        encoder: Encoder,
        value: RuleDto,
    ) {
        val output =
            encoder as? JsonEncoder ?: throw SerializationException("Holiday rules can only be encoded to JSON")
        val json = output.json
        val (type, fields) =
            when (value) {
                is RuleDto.Fixed -> {
                    "fixed" to json.encodeToJsonElement(RuleDto.Fixed.serializer(), value)
                }

                is RuleDto.NthWeekday -> {
                    "nthWeekday" to
                        json.encodeToJsonElement(RuleDto.NthWeekday.serializer(), value)
                }

                is RuleDto.WeekdayRelative -> {
                    "weekdayRelative" to json.encodeToJsonElement(RuleDto.WeekdayRelative.serializer(), value)
                }

                is RuleDto.Offset -> {
                    "offset" to json.encodeToJsonElement(RuleDto.Offset.serializer(), value)
                }

                is RuleDto.Easter -> {
                    "easter" to json.encodeToJsonElement(RuleDto.Easter.serializer(), value)
                }

                is RuleDto.Table -> {
                    "table" to json.encodeToJsonElement(RuleDto.Table.serializer(), value)
                }

                is RuleDto.Ifc -> {
                    "ifc" to json.encodeToJsonElement(RuleDto.Ifc.serializer(), value)
                }
            }
        val withType = LinkedHashMap<String, JsonElement>(fields.jsonObject.size + 1)
        withType[DISCRIMINATOR] = JsonPrimitive(type)
        withType.putAll(fields.jsonObject)
        output.encodeJsonElement(JsonObject(withType))
    }
}
