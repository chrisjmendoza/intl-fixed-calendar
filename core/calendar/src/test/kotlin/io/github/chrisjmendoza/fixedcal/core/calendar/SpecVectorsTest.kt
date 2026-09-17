package io.github.chrisjmendoza.fixedcal.core.calendar

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

// Verifies spec §6.2–§6.4: every test-vector row, parsed from docs/calendar-spec.md itself (anti-doc-drift).
class SpecVectorsTest {
    private data class Vector(
        val section: String,
        val line: Int,
        val gregorian: LocalDate,
        val ifcText: String,
        val numeric: String,
        val dayOfYear: Int,
        val actualDayOfWeek: DayOfWeek,
        val nominalDayOfWeek: DayOfWeek?,
        val weekOfYear: Int?,
        val intercalary: Boolean,
    )

    private val vectors: List<Vector> by lazy { parseVectors(specFile()) }

    @Test
    fun `the spec contains exactly 105 vectors split 27 plus 28 plus 50`() {
        assertSoftly {
            withClue("rows in 6.2") { vectors.count { it.section == "6.2" } shouldBe 27 }
            withClue("rows in 6.3") { vectors.count { it.section == "6.3" } shouldBe 28 }
            withClue("rows in 6.4") { vectors.count { it.section == "6.4" } shouldBe 50 }
            withClue("total rows") { vectors.size shouldBe 105 }
        }
    }

    @TestFactory
    fun `every spec vector holds in both directions`(): List<DynamicTest> =
        vectors.map { vector ->
            dynamicTest("section ${vector.section} line ${vector.line}: ${vector.gregorian} = ${vector.ifcText}") {
                verify(vector)
            }
        }

    private fun verify(vector: Vector) {
        assertSoftly {
            withClue("the spec's own 'Real weekday' cell against java.time") {
                vector.gregorian.dayOfWeek shouldBe vector.actualDayOfWeek
            }
            val converted = IfcDate.from(vector.gregorian)
            withClue("from(${vector.gregorian}).toNumericString()") {
                converted.toNumericString() shouldBe vector.numeric
            }
            val parsed = IfcDate.parse(vector.numeric)
            withClue("parse(${vector.numeric}).toLocalDate()") { parsed.toLocalDate() shouldBe vector.gregorian }
            withClue("parse(${vector.numeric}) == from(${vector.gregorian})") { parsed shouldBe converted }
            withClue("from(${vector.gregorian})") { verifyFields(converted, vector) }
            withClue("parse(${vector.numeric})") { verifyFields(parsed, vector) }
        }
    }

    private fun verifyFields(
        date: IfcDate,
        vector: Vector,
    ) {
        withClue("year") { date.year shouldBe vector.gregorian.year }
        withClue("dayOfYear") { date.dayOfYear shouldBe vector.dayOfYear }
        withClue("actualDayOfWeek") { date.actualDayOfWeek shouldBe vector.actualDayOfWeek }
        withClue("nominalDayOfWeek") { date.nominalDayOfWeek shouldBe vector.nominalDayOfWeek }
        withClue("weekOfYear") { date.weekOfYear shouldBe vector.weekOfYear }
        withClue("isIntercalary") { date.isIntercalary shouldBe vector.intercalary }
        withClue("human-readable IFC cell '${vector.ifcText}'") { describe(date) shouldBe normalize(vector.ifcText) }
    }

    /** Renders [date] from its type and fields in the shape of the spec's "IFC" column, lower-cased. */
    private fun describe(date: IfcDate): String =
        when (date) {
            is IfcDate.LeapDay -> "leap day ${date.year}"
            is IfcDate.YearDay -> "year day ${date.year}"
            is IfcDate.Regular -> "${date.month.name.lowercase(Locale.ROOT)} ${date.dayOfMonth}, ${date.year}"
        }

    private fun normalize(ifcText: String): String {
        val intercalary = INTERCALARY_TEXT.matchEntire(ifcText)
        if (intercalary != null) {
            val (kind, year) = intercalary.destructured
            return "${kind.lowercase(Locale.ROOT)} day ${year.toInt()}"
        }
        val regular =
            checkNotNull(REGULAR_TEXT.matchEntire(ifcText)) { "Unrecognized 'IFC' cell in the spec: '$ifcText'" }
        val (month, day, year) = regular.destructured
        return "${month.lowercase(Locale.ROOT)} ${day.toInt()}, ${year.toInt()}"
    }

    private fun specFile(): File {
        val path =
            checkNotNull(System.getProperty(SPEC_PROPERTY)) {
                "System property '$SPEC_PROPERTY' is not set. Run the tests through Gradle (:core:calendar:test), " +
                    "or pass -D$SPEC_PROPERTY=<path to docs/calendar-spec.md>."
            }
        val file = File(path)
        check(file.isFile) { "Spec file named by '$SPEC_PROPERTY' does not exist: $path" }
        return file
    }

    private fun parseVectors(file: File): List<Vector> {
        val result = mutableListOf<Vector>()
        var section: String? = null
        file.readLines(Charsets.UTF_8).forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("#")) {
                section = VECTOR_SECTIONS.firstOrNull { line.startsWith("### $it ") }
            } else if (section != null && ISO_DATE_ROW.containsMatchIn(line)) {
                result += parseRow(checkNotNull(section), index + 1, line)
            }
        }
        return result
    }

    private fun parseRow(
        section: String,
        lineNumber: Int,
        line: String,
    ): Vector {
        val cells =
            line
                .removePrefix("|")
                .removeSuffix("|")
                .split("|")
                .map { it.trim() }
        check(cells.size == COLUMNS) { "Spec line $lineNumber: expected $COLUMNS cells but found ${cells.size}: $line" }
        val nominalCell = cells[5]
        val weekCell = cells[6]
        check((nominalCell == DASH) == (weekCell == DASH)) {
            "Spec line $lineNumber: nominal weekday and week must both be '$DASH' or neither: $line"
        }
        val numericCell = cells[2]
        check(numericCell.length > 2 && numericCell.startsWith("`") && numericCell.endsWith("`")) {
            "Spec line $lineNumber: the 'IFC numeric' cell must be in backticks: $line"
        }
        return Vector(
            section = section,
            line = lineNumber,
            gregorian = LocalDate.parse(cells[0]),
            ifcText = cells[1],
            numeric = numericCell.removeSurrounding("`"),
            dayOfYear = cells[3].toInt(),
            actualDayOfWeek = weekday(cells[4]),
            nominalDayOfWeek = if (nominalCell == DASH) null else weekday(nominalCell),
            weekOfYear = if (weekCell == DASH) null else weekCell.toInt(),
            intercalary = nominalCell == DASH,
        )
    }

    private fun weekday(cell: String): DayOfWeek = DayOfWeek.valueOf(cell.uppercase(Locale.ROOT))

    private companion object {
        const val SPEC_PROPERTY = "ifc.calendarSpec"
        const val COLUMNS = 8
        const val DASH = "—"
        val VECTOR_SECTIONS = listOf("6.2", "6.3", "6.4")
        val ISO_DATE_ROW = Regex("""^\|\s*\d{4}-\d{2}-\d{2}\s*\|""")
        val INTERCALARY_TEXT = Regex("""^(Leap|Year) Day (\d{1,4})$""")
        val REGULAR_TEXT = Regex("""^(\p{L}+) (\d{1,2}), (\d{1,4})$""")
    }
}
