package io.github.chrisjmendoza.yearal.core.domain.holiday

import java.time.LocalDate

/**
 * Easter Sunday algorithms. Internal: reached through [HolidayRule.Easter].
 *
 * Spec: `docs/holidays-and-import.md` §2.2 rule 5.
 */
internal object Computus {
    /** Gregorian Easter Sunday for [year] by the Meeus/Jones/Butcher algorithm (valid for every year). */
    fun western(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val monthAndDay = h + l - 7 * m + 114
        return LocalDate.of(year, monthAndDay / 31, monthAndDay % 31 + 1)
    }

    /**
     * Orthodox Easter Sunday for [year] as a Gregorian date: the Julian-calendar computus (Meeus),
     * then the Julian→Gregorian shift for that year.
     *
     * The shift is applied by adding days to the proleptic-Gregorian date with the same fields, which
     * is exact here because Julian Easter is always after March 1, where the two calendars' month
     * lengths agree until the next century boundary.
     */
    fun orthodox(year: Int): LocalDate {
        val a = year % 4
        val b = year % 7
        val c = year % 19
        val d = (19 * c + 15) % 30
        val e = (2 * a + 4 * b - d + 34) % 7
        val monthAndDay = d + e + 114
        val julian = LocalDate.of(year, monthAndDay / 31, monthAndDay % 31 + 1)
        return julian.plusDays(julianToGregorianShift(year).toLong())
    }

    /** Days to add to a Julian date after March 1 of [year] to obtain the Gregorian date: 13 for 1900–2099. */
    internal fun julianToGregorianShift(year: Int): Int = year / 100 - year / 400 - 2
}
