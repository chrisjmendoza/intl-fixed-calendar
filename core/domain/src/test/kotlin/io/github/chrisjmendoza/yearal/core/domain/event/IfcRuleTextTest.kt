package io.github.chrisjmendoza.yearal.core.domain.event

import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.LocalDate

// The rule text of docs/ARCHITECTURE.md §3.2 (`ifc_rule` / X-IFC-RRULE) with the grammar frozen in
// docs/contracts/Events.md and docs/adr/0005-events-contract.md decision 1. Expected strings are written
// out by hand from that grammar, never produced by the code under test.
class IfcRuleTextTest {
    // --- canonical form ---------------------------------------------------------------------------------

    @Test
    fun `formats the three examples of ARCHITECTURE 3_2 in canonical form`() {
        assertSoftly {
            IfcRuleText.format(IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13)) shouldBe "IFC;FREQ=YEARLY;MONTH=7;DAY=13"
            IfcRuleText.format(IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.YearDay)) shouldBe
                "IFC;FREQ=YEARLY;INTERCALARY=YEAR_DAY"
            // The doc's third example spells out INTERVAL=1; the canonical form leaves the default out.
            IfcRuleText.format(IfcRecurrence.MonthlyOnDay(13)) shouldBe "IFC;FREQ=MONTHLY;DAY=13"
        }
    }

    @Test
    fun `IFC July is month 8 and December is month 13, not the Gregorian numbers`() {
        assertSoftly {
            IfcRuleText.format(IfcRecurrence.YearlyOnDate(IfcMonth.JULY, 4)) shouldBe "IFC;FREQ=YEARLY;MONTH=8;DAY=4"
            IfcRuleText.format(IfcRecurrence.YearlyOnDate(IfcMonth.DECEMBER, 28)) shouldBe
                "IFC;FREQ=YEARLY;MONTH=13;DAY=28"
            IfcRuleText.parse("IFC;FREQ=YEARLY;MONTH=13;DAY=28") shouldBe
                IfcRecurrence.YearlyOnDate(IfcMonth.DECEMBER, 28)
        }
    }

    @Test
    fun `Leap Day always writes its common-year policy and Year Day never does`() {
        assertSoftly {
            for (policy in LeapDayPolicy.entries) {
                IfcRuleText.format(IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.LeapDay(policy))) shouldBe
                    "IFC;FREQ=YEARLY;INTERCALARY=LEAP_DAY;COMMONYEAR=${policy.name}"
            }
            // The default policy for user-created events is JUNE_28 (ARCHITECTURE reconciled decision 4).
            IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.LeapDay()).toRuleText() shouldBe
                "IFC;FREQ=YEARLY;INTERCALARY=LEAP_DAY;COMMONYEAR=JUNE_28"
        }
    }

    @Test
    fun `interval and end conditions follow the position, in that order`() {
        assertSoftly {
            IfcRuleText.format(IfcRecurrence.MonthlyOnDay(1, interval = 2)) shouldBe "IFC;FREQ=MONTHLY;DAY=1;INTERVAL=2"
            IfcRuleText.format(
                IfcRecurrence.YearlyOnDate(IfcMonth.MARCH, 4, end = RecurrenceEnd.Until(LocalDate.of(2030, 3, 1))),
            ) shouldBe "IFC;FREQ=YEARLY;MONTH=3;DAY=4;UNTIL=20300301"
            IfcRuleText.format(
                IfcRecurrence.YearlyOnIntercalary(
                    IntercalaryDay.LeapDay(LeapDayPolicy.SKIP),
                    interval = 4,
                    end = RecurrenceEnd.Count(10),
                ),
            ) shouldBe "IFC;FREQ=YEARLY;INTERCALARY=LEAP_DAY;COMMONYEAR=SKIP;INTERVAL=4;COUNT=10"
            // A year below 1000 is zero-padded to four digits.
            IfcRuleText.format(
                IfcRecurrence.MonthlyOnDay(28, end = RecurrenceEnd.Until(LocalDate.of(987, 1, 5))),
            ) shouldBe "IFC;FREQ=MONTHLY;DAY=28;UNTIL=09870105"
        }
    }

    @Test
    fun `the longest canonical text fits MAX_LENGTH`() {
        val longest =
            IfcRecurrence.YearlyOnIntercalary(
                IntercalaryDay.LeapDay(LeapDayPolicy.JUNE_28),
                interval = Int.MAX_VALUE,
                end = RecurrenceEnd.Count(Int.MAX_VALUE),
            )
        val text = IfcRuleText.format(longest)
        text shouldBe "IFC;FREQ=YEARLY;INTERCALARY=LEAP_DAY;COMMONYEAR=JUNE_28;INTERVAL=2147483647;COUNT=2147483647"
        text.length shouldBe 92
        text.length shouldBeLessThanOrEqual IfcRuleText.MAX_LENGTH
        IfcRuleText.parse(text) shouldBe longest
    }

    // --- parsing ----------------------------------------------------------------------------------------

    @Test
    fun `parses the ARCHITECTURE 3_2 examples, including the explicit INTERVAL=1`() {
        assertSoftly {
            IfcRuleText.parse("IFC;FREQ=YEARLY;MONTH=7;DAY=13") shouldBe IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13)
            IfcRuleText.parse("IFC;FREQ=YEARLY;INTERCALARY=YEAR_DAY") shouldBe
                IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.YearDay)
            IfcRuleText.parse("IFC;FREQ=MONTHLY;DAY=13;INTERVAL=1") shouldBe IfcRecurrence.MonthlyOnDay(13)
            // ... and re-serialising drops the default, the only non-canonical spelling accepted.
            IfcRuleText.format(IfcRuleText.parse("IFC;FREQ=MONTHLY;DAY=13;INTERVAL=1")) shouldBe
                "IFC;FREQ=MONTHLY;DAY=13"
        }
    }

    @Test
    fun `parses every Leap Day policy, UNTIL and COUNT`() {
        assertSoftly {
            IfcRuleText.parse("IFC;FREQ=YEARLY;INTERCALARY=LEAP_DAY;COMMONYEAR=SOL_1") shouldBe
                IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.LeapDay(LeapDayPolicy.SOL_1))
            IfcRuleText.parse("IFC;FREQ=YEARLY;INTERCALARY=LEAP_DAY;COMMONYEAR=SKIP;INTERVAL=4") shouldBe
                IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.LeapDay(LeapDayPolicy.SKIP), interval = 4)
            IfcRuleText.parse("IFC;FREQ=YEARLY;INTERCALARY=YEAR_DAY;UNTIL=99991231") shouldBe
                IfcRecurrence.YearlyOnIntercalary(
                    IntercalaryDay.YearDay,
                    end = RecurrenceEnd.Until(LocalDate.of(9999, 12, 31)),
                )
            IfcRuleText.parse("IFC;FREQ=MONTHLY;DAY=28;INTERVAL=13;COUNT=1") shouldBe
                IfcRecurrence.MonthlyOnDay(28, interval = 13, end = RecurrenceEnd.Count(1))
            // UNTIL is a Gregorian date: Feb 29 is real in 2028.
            IfcRuleText.parse("IFC;FREQ=YEARLY;MONTH=1;DAY=1;UNTIL=20280229") shouldBe
                IfcRecurrence.YearlyOnDate(IfcMonth.JANUARY, 1, end = RecurrenceEnd.Until(LocalDate.of(2028, 2, 29)))
        }
    }

    @Test
    fun `format and parse round-trip for every rule`() {
        runBlocking {
            checkAll(2_000, ruleArb) { rule ->
                val text = IfcRuleText.format(rule)
                withClue(text) {
                    IfcRuleText.parse(text) shouldBe rule
                    IfcRuleText.parseOrNull(text) shouldBe rule
                    // Canonical text is a fixed point.
                    IfcRuleText.format(IfcRuleText.parse(text)) shouldBe text
                    rule.toRuleText() shouldBe text
                }
            }
        }
    }

    // --- rejection --------------------------------------------------------------------------------------

    @Test
    fun `rejects unknown, repeated and misplaced keys`() {
        rejectAll(
            "IFC;FREQ=YEARLY;MONTH=7;DAY=13;WKST=MO",
            "IFC;FREQ=YEARLY;MONTH=7;DAY=13;BYDAY=FR",
            "IFC;FREQ=YEARLY;MONTH=7;DAY=13;X-FOO=1",
            "IFC;FREQ=YEARLY;MONTH=7;DAY=13;DAY=13",
            "IFC;FREQ=YEARLY;MONTH=7;MONTH=7;DAY=13",
            "IFC;FREQ=YEARLY;MONTH=7;DAY=13;INTERVAL=2;INTERVAL=2",
            // Known keys in another order.
            "IFC;FREQ=YEARLY;DAY=13;MONTH=7",
            "IFC;MONTH=7;DAY=13;FREQ=YEARLY",
            "IFC;FREQ=YEARLY;MONTH=7;DAY=13;COUNT=3;INTERVAL=2",
            "IFC;FREQ=MONTHLY;INTERVAL=2;DAY=13",
            // Keys that do not belong to the position.
            "IFC;FREQ=MONTHLY;MONTH=7;DAY=13",
            "IFC;FREQ=MONTHLY;INTERCALARY=YEAR_DAY",
            "IFC;FREQ=YEARLY;MONTH=7;DAY=13;INTERCALARY=YEAR_DAY",
            "IFC;FREQ=YEARLY;INTERCALARY=YEAR_DAY;DAY=13",
        )
    }

    @Test
    fun `rejects a missing, misplaced or unknown Leap Day policy`() {
        rejectAll(
            // Required with LEAP_DAY: data at rest never depends on a default policy.
            "IFC;FREQ=YEARLY;INTERCALARY=LEAP_DAY",
            "IFC;FREQ=YEARLY;INTERCALARY=LEAP_DAY;INTERVAL=4",
            "IFC;FREQ=YEARLY;INTERCALARY=LEAP_DAY;INTERVAL=4;COMMONYEAR=SKIP",
            // Forbidden everywhere else.
            "IFC;FREQ=YEARLY;INTERCALARY=YEAR_DAY;COMMONYEAR=SKIP",
            "IFC;FREQ=YEARLY;MONTH=6;DAY=28;COMMONYEAR=JUNE_28",
            "IFC;FREQ=MONTHLY;DAY=28;COMMONYEAR=JUNE_28",
            // Unknown values, including the RFC 7529 names the architecture did not adopt.
            "IFC;FREQ=YEARLY;INTERCALARY=LEAP_DAY;COMMONYEAR=OMIT",
            "IFC;FREQ=YEARLY;INTERCALARY=LEAP_DAY;COMMONYEAR=BACKWARD",
            "IFC;FREQ=YEARLY;INTERCALARY=LEAP_DAY;COMMONYEAR=june_28",
            "IFC;FREQ=YEARLY;INTERCALARY=LEAP_DAY;COMMONYEAR=",
            "IFC;FREQ=YEARLY;INTERCALARY=SOL_DAY",
            "IFC;FREQ=YEARLY;INTERCALARY=",
        )
    }

    @Test
    fun `rejects out-of-range and malformed numbers`() {
        rejectAll(
            "IFC;FREQ=YEARLY;MONTH=0;DAY=13",
            "IFC;FREQ=YEARLY;MONTH=14;DAY=13",
            "IFC;FREQ=YEARLY;MONTH=7;DAY=0",
            "IFC;FREQ=YEARLY;MONTH=7;DAY=29",
            "IFC;FREQ=YEARLY;MONTH=6;DAY=29",
            "IFC;FREQ=MONTHLY;DAY=29",
            "IFC;FREQ=MONTHLY;DAY=13;INTERVAL=0",
            "IFC;FREQ=MONTHLY;DAY=13;COUNT=0",
            "IFC;FREQ=MONTHLY;DAY=13;INTERVAL=2147483648",
            "IFC;FREQ=MONTHLY;DAY=13;COUNT=99999999999",
            // Sign, leading zero, empty, non-digits, non-ASCII digits.
            "IFC;FREQ=MONTHLY;DAY=-13",
            "IFC;FREQ=MONTHLY;DAY=+13",
            "IFC;FREQ=YEARLY;MONTH=07;DAY=13",
            "IFC;FREQ=MONTHLY;DAY=013",
            "IFC;FREQ=MONTHLY;DAY=",
            "IFC;FREQ=MONTHLY;DAY=1.0",
            "IFC;FREQ=MONTHLY;DAY=XIII",
            "IFC;FREQ=MONTHLY;DAY=١٣",
        )
    }

    @Test
    fun `rejects bad UNTIL dates and UNTIL together with COUNT`() {
        rejectAll(
            "IFC;FREQ=MONTHLY;DAY=13;UNTIL=20301231;COUNT=5",
            "IFC;FREQ=MONTHLY;DAY=13;COUNT=5;UNTIL=20301231",
            "IFC;FREQ=MONTHLY;DAY=13;UNTIL=2030-12-31",
            "IFC;FREQ=MONTHLY;DAY=13;UNTIL=20301231T000000Z",
            "IFC;FREQ=MONTHLY;DAY=13;UNTIL=2030123",
            "IFC;FREQ=MONTHLY;DAY=13;UNTIL=",
            // Not real Gregorian dates: month 13 is an IFC month, not a Gregorian one.
            "IFC;FREQ=MONTHLY;DAY=13;UNTIL=20301301",
            "IFC;FREQ=MONTHLY;DAY=13;UNTIL=20270229",
            "IFC;FREQ=MONTHLY;DAY=13;UNTIL=20300631",
            "IFC;FREQ=MONTHLY;DAY=13;UNTIL=20300100",
            // Year 0 is outside 1..9999.
            "IFC;FREQ=MONTHLY;DAY=13;UNTIL=00000101",
        )
    }

    @Test
    fun `rejects anything that is not exactly the grammar`() {
        rejectAll(
            "",
            "IFC",
            "IFC;",
            "IFC;FREQ=YEARLY",
            "IFC;FREQ=YEARLY;MONTH=7",
            "IFC;FREQ=MONTHLY",
            "IFC;FREQ=DAILY;DAY=13",
            "IFC;FREQ=WEEKLY",
            "FREQ=YEARLY;MONTH=7;DAY=13",
            "RRULE:IFC;FREQ=YEARLY;MONTH=7;DAY=13",
            "X-IFC-RRULE:IFC;FREQ=YEARLY;MONTH=7;DAY=13",
            "RSCALE=X-IFC;FREQ=YEARLY;BYMONTH=7;BYMONTHDAY=13;SKIP=OMIT",
            // Case, whitespace, empty segments, trailing separator.
            "ifc;freq=yearly;month=7;day=13",
            "IFC;FREQ=yearly;MONTH=7;DAY=13",
            "IFC;Freq=YEARLY;MONTH=7;DAY=13",
            " IFC;FREQ=YEARLY;MONTH=7;DAY=13",
            "IFC;FREQ=YEARLY;MONTH=7;DAY=13 ",
            "IFC; FREQ=YEARLY;MONTH=7;DAY=13",
            "IFC;FREQ=YEARLY;MONTH=7;DAY=13\n",
            "IFC;FREQ=YEARLY;MONTH=7;DAY=13;",
            "IFC;;FREQ=YEARLY;MONTH=7;DAY=13",
            "IFC;FREQ=YEARLY;MONTH=7;;DAY=13",
            "IFC;FREQ=YEARLY;MONTH;DAY=13",
            "IFC;FREQ=YEARLY;MONTH=7=7;DAY=13",
            "IFC,FREQ=YEARLY,MONTH=7,DAY=13",
        )
    }

    @Test
    fun `rejects text longer than MAX_LENGTH before looking at it`() {
        val padded = "IFC;FREQ=YEARLY;MONTH=7;DAY=13" + ";".repeat(IfcRuleText.MAX_LENGTH)
        shouldThrow<IllegalArgumentException> { IfcRuleText.parse(padded) }
        IfcRuleText.parseOrNull(padded).shouldBeNull()
    }

    private fun rejectAll(vararg texts: String) {
        assertSoftly {
            for (text in texts) {
                withClue("\"$text\" must be rejected") {
                    shouldThrow<IllegalArgumentException> { IfcRuleText.parse(text) }
                    IfcRuleText.parseOrNull(text).shouldBeNull()
                }
            }
        }
    }

    private companion object {
        val endArb: Arb<RecurrenceEnd> =
            Arb.choice(
                Arb.constant(RecurrenceEnd.Never),
                Arb.int(1..Int.MAX_VALUE).map { RecurrenceEnd.Count(it) },
                arbitrary {
                    RecurrenceEnd.Until(LocalDate.ofYearDay(Arb.int(1..9999).bind(), Arb.int(1..365).bind()))
                },
            )

        val intercalaryArb: Arb<IntercalaryDay> =
            Arb.choice(
                Arb.constant(IntercalaryDay.YearDay),
                Arb.enum<LeapDayPolicy>().map { IntercalaryDay.LeapDay(it) },
            )

        val intervalArb: Arb<Int> = Arb.choice(Arb.int(1..12), Arb.int(1..Int.MAX_VALUE))

        val ruleArb: Arb<IfcRecurrence> =
            Arb.choice(
                arbitrary {
                    IfcRecurrence.YearlyOnDate(
                        Arb.enum<IfcMonth>().bind(),
                        Arb.int(1..28).bind(),
                        intervalArb.bind(),
                        endArb.bind(),
                    )
                },
                arbitrary {
                    IfcRecurrence.YearlyOnIntercalary(
                        intercalaryArb.bind(),
                        intervalArb.bind(),
                        endArb.bind(),
                    )
                },
                arbitrary { IfcRecurrence.MonthlyOnDay(Arb.int(1..28).bind(), intervalArb.bind(), endArb.bind()) },
            )
    }
}
