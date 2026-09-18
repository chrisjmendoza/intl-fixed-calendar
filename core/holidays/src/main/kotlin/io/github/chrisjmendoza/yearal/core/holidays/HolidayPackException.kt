package io.github.chrisjmendoza.yearal.core.holidays

/**
 * A holiday pack could not be loaded: the JSON is malformed, uses an unknown key or rule type, or
 * describes a holiday the domain model rejects (a day outside its month, a duplicate id, a name
 * without `"en"`, a table date outside its key year, …).
 *
 * A pack either loads completely or not at all; this exception is the "not at all" case, and it always
 * names the pack so the failure can be traced to a file. The message is for developers and logs, not
 * for users — pack contents are static app data, so a failure here is a packaging bug, never a user
 * error.
 *
 * Spec: `docs/holidays-and-import.md` §2.4; `docs/adr/0004-holiday-pack-format.md`.
 *
 * @property packName the pack that failed, as passed to [HolidayPackLoader.load] or
 *   [HolidayPackLoader.loadBundled] (`"US"`, `"ifc"`, …).
 * @property holidayId the id of the holiday whose definition failed, or `null` when the failure is
 *   not attributable to one holiday (a syntax error, a missing top-level field, a duplicate id).
 */
public class HolidayPackException(
    public val packName: String,
    public val holidayId: String?,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(
        buildString {
            append("Holiday pack '").append(packName).append("'")
            if (holidayId != null) append(", holiday '").append(holidayId).append("'")
            append(": ").append(message)
        },
        cause,
    )
