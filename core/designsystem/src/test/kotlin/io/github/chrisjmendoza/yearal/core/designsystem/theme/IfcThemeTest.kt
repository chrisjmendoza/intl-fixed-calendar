package io.github.chrisjmendoza.yearal.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.assertions.withClue
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [IfcTheme] with dynamic colour off yields the brand schemes seeded from the launcher palette
 * (docs/ROADMAP.md decision #10), and every text-on-container pair the grid relies on meets WCAG AA
 * (4.5:1) in both modes.
 */
@RunWith(AndroidJUnit4::class)
class IfcThemeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun schemeOf(darkTheme: Boolean): ColorScheme {
        var scheme: ColorScheme? = null
        compose.setContent {
            IfcTheme(darkTheme = darkTheme, dynamicColor = false) {
                scheme = MaterialTheme.colorScheme
            }
        }
        return checkNotNull(scheme)
    }

    @Test
    fun `light scheme without dynamic colour is seeded from the brand teal`() {
        val scheme = schemeOf(darkTheme = false)

        scheme.primary shouldBe BrandTeal
        scheme.onPrimary shouldBe BrandCream
        scheme.surfaceContainer shouldBe BrandCream
        scheme.tertiaryContainer shouldBe LightTertiaryContainer
    }

    @Test
    fun `dark scheme without dynamic colour keeps the brand teal as the primary container`() {
        val scheme = schemeOf(darkTheme = true)

        scheme.primary shouldBe DarkPrimary
        scheme.primaryContainer shouldBe BrandTeal
        scheme.tertiaryContainer shouldBe DarkTertiaryContainer
        scheme.surface shouldNotBe BrandLightColorScheme.surface
    }

    @Test
    fun `light brand scheme meets 4_5 to 1 on every pair the grid uses`() {
        assertGridContrast(BrandLightColorScheme)
    }

    @Test
    fun `dark brand scheme meets 4_5 to 1 on every pair the grid uses`() {
        assertGridContrast(BrandDarkColorScheme)
    }

    // The pairs the month grid draws text or the today ring in (DayCell, IntercalaryBand, headers).
    private fun assertGridContrast(scheme: ColorScheme) {
        val pairs =
            listOf(
                "onPrimary/primary" to (scheme.onPrimary to scheme.primary),
                "onPrimaryContainer/primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
                "onTertiaryContainer/tertiaryContainer" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
                "onSecondaryContainer/secondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
                "onSurface/surface" to (scheme.onSurface to scheme.surface),
                "onSurfaceVariant/surface" to (scheme.onSurfaceVariant to scheme.surface),
                "primary/surface" to (scheme.primary to scheme.surface),
                "primary/primaryContainer" to (scheme.primary to scheme.primaryContainer),
                "primary/tertiaryContainer" to (scheme.primary to scheme.tertiaryContainer),
                "tertiary/surface" to (scheme.tertiary to scheme.surface),
            )
        pairs.forEach { (name, colors) ->
            val (foreground, background) = colors
            withClue(name) {
                contrastRatio(foreground, background) shouldBeGreaterThanOrEqual MINIMUM_TEXT_CONTRAST
            }
        }
    }

    private fun contrastRatio(
        a: Color,
        b: Color,
    ): Double {
        val lighter = maxOf(a.luminance(), b.luminance()).toDouble()
        val darker = minOf(a.luminance(), b.luminance()).toDouble()
        return (lighter + LUMINANCE_OFFSET) / (darker + LUMINANCE_OFFSET)
    }

    private companion object {
        const val MINIMUM_TEXT_CONTRAST = 4.5
        const val LUMINANCE_OFFSET = 0.05
    }
}
