package io.github.chrisjmendoza.yearal.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// The brand palette from the launcher icon (docs/brand/README.md; docs/ROADMAP.md decision #10). The
// same four values live in app/src/main/res/values/ic_launcher_background.xml for the icon layers;
// keep both in step.

/** Brand teal `#123F3D`: the icon background and the light scheme's primary. */
val BrandTeal: Color = Color(0xFF123F3D)

/** Brand cream `#F4ECDA`: the icon's dot colour, text on teal and the light scheme's paper tone. */
val BrandCream: Color = Color(0xFFF4ECDA)

/** Brand accent `#F28C28`: the icon's Year Day pill; the source hue of the tertiary family. */
val BrandAccent: Color = Color(0xFFF28C28)

/** Brand ink `#244338`: the monochrome icon glyph; the source hue of the secondary family. */
val BrandInk: Color = Color(0xFF244338)

// Scheme tones. Contrast ratios (WCAG 2 relative luminance) are noted where text sits on colour;
// every text-on-container pair is above 4.5:1 and most are above 7:1. Declaration order matters:
// top-level vals initialise in file order, so a tone is declared before the first tone that reuses it.

/** Dark primary, a light teal-mint; [DarkOnPrimary] on it: 7.8:1, on [DarkSurface]: 10.9:1. */
val DarkPrimary: Color = Color(0xFF8FD4CA)

/** Light primary = [BrandTeal]. [LightOnPrimary] on it: 9.9:1. On [LightSurface]: 10.8:1. */
val LightPrimary: Color = BrandTeal

/** Light on-primary = [BrandCream]. */
val LightOnPrimary: Color = BrandCream

/** Light primary container, a pale teal-mint; [LightOnPrimaryContainer] on it: 12.3:1. */
val LightPrimaryContainer: Color = Color(0xFFB4E4DD)

/** Light on-primary-container, near-black teal. */
val LightOnPrimaryContainer: Color = Color(0xFF00201E)

/** Light inverse primary = [DarkPrimary]; on [LightInverseSurface]: 7.6:1. */
val LightInversePrimary: Color = DarkPrimary

/** Light secondary, a muted green from [BrandInk]; white on it: 6.5:1, on [LightSurface]: 6.0:1. */
val LightSecondary: Color = Color(0xFF4A635B)

/** Light on-secondary. */
val LightOnSecondary: Color = Color(0xFFFFFFFF)

/** Light secondary container, a pale sage; [LightOnSecondaryContainer] on it: 13.2:1. */
val LightSecondaryContainer: Color = Color(0xFFCDE8DC)

/** Light on-secondary-container. */
val LightOnSecondaryContainer: Color = Color(0xFF06201A)

/** Light tertiary, a dark amber from [BrandAccent]; white on it: 6.8:1, on [LightSurface]: 6.3:1. */
val LightTertiary: Color = Color(0xFF8A4B00)

/** Light on-tertiary. */
val LightOnTertiary: Color = Color(0xFFFFFFFF)

/**
 * Light tertiary container, a warm peach tint of [BrandAccent]: the intercalary band's fill
 * (docs/ARCHITECTURE.md §4). [LightOnTertiaryContainer] on it: 13.3:1; [LightOnSurfaceVariant] on
 * it: 7.2:1.
 */
val LightTertiaryContainer: Color = Color(0xFFFFDCBE)

/** Light on-tertiary-container, a deep brown. */
val LightOnTertiaryContainer: Color = Color(0xFF2E1500)

/** Light surface and background, a whisper of [BrandCream]; [LightOnSurface] on it: 14.9:1. */
val LightSurface: Color = Color(0xFFFBF6EA)

/** Light on-surface, ink-tinted near-black. */
val LightOnSurface: Color = Color(0xFF1A2320)

/** Light surface variant, the mint-grey of the monochrome icon; [LightOnSurfaceVariant] on it: 7.4:1. */
val LightSurfaceVariant: Color = Color(0xFFDCE7DF)

/** Light on-surface-variant; on [LightSurface]: 8.7:1. */
val LightOnSurfaceVariant: Color = Color(0xFF3F4945)

/** Light outline; on [LightSurface]: 4.2:1 (above the 3:1 floor for non-text UI). */
val LightOutline: Color = Color(0xFF6F7975)

/** Light outline variant, for hairlines. */
val LightOutlineVariant: Color = Color(0xFFBEC9C4)

/** Light inverse surface. */
val LightInverseSurface: Color = Color(0xFF2F3331)

/** Light inverse on-surface; on [LightInverseSurface]: 11.3:1. */
val LightInverseOnSurface: Color = Color(0xFFF0F1EE)

/** Light error (Material baseline red); white on it: 6.5:1. */
val LightError: Color = Color(0xFFBA1A1A)

/** Light on-error. */
val LightOnError: Color = Color(0xFFFFFFFF)

/** Light error container; [LightOnErrorContainer] on it: 13.3:1. */
val LightErrorContainer: Color = Color(0xFFFFDAD6)

/** Light on-error-container. */
val LightOnErrorContainer: Color = Color(0xFF410002)

/** Light surface container tiers: lowest (white) to highest (a darker cream). */
val LightSurfaceContainerLowest: Color = Color(0xFFFFFFFF)

/** See [LightSurfaceContainerLowest]. */
val LightSurfaceContainerLow: Color = Color(0xFFF8F1E2)

/** The brand cream itself: cards and sheets sit on it. */
val LightSurfaceContainer: Color = BrandCream

/** See [LightSurfaceContainerLowest]. */
val LightSurfaceContainerHigh: Color = Color(0xFFECE4D0)

/** See [LightSurfaceContainerLowest]; [LightOnSurface] on it: 11.9:1, [LightPrimary]: 8.6:1. */
val LightSurfaceContainerHighest: Color = Color(0xFFE5DDC8)

/** Light surface dim. */
val LightSurfaceDim: Color = Color(0xFFDCD6CA)

/** Light surface bright. */
val LightSurfaceBright: Color = LightSurface

// Dark scheme tones: surfaces are near-black teal, the brand teal returns as the primary container.

/** Dark on-primary, a deep teal. */
val DarkOnPrimary: Color = Color(0xFF003733)

/** Dark primary container = [BrandTeal]; [DarkOnPrimaryContainer] on it: 9.1:1. */
val DarkPrimaryContainer: Color = BrandTeal

/** Dark on-primary-container, a pale mint. */
val DarkOnPrimaryContainer: Color = Color(0xFFABF0E6)

/** Dark inverse primary = [BrandTeal]; on [DarkInverseSurface]: 9.0:1. */
val DarkInversePrimary: Color = BrandTeal

/** Dark secondary, a pale sage; [DarkOnSecondary] on it: 7.7:1, on [DarkSurface]: 10.7:1. */
val DarkSecondary: Color = Color(0xFFB1CCC1)

/** Dark on-secondary. */
val DarkOnSecondary: Color = Color(0xFF1D352D)

/** Dark secondary container; [DarkOnSecondaryContainer] on it: 7.3:1. */
val DarkSecondaryContainer: Color = Color(0xFF334B43)

/** Dark on-secondary-container. */
val DarkOnSecondaryContainer: Color = Color(0xFFCDE8DC)

/** Dark tertiary, a light amber; [DarkOnTertiary] on it: 7.9:1, on [DarkSurface]: 10.8:1. */
val DarkTertiary: Color = Color(0xFFFFB877)

/** Dark on-tertiary. */
val DarkOnTertiary: Color = Color(0xFF4A2600)

/**
 * Dark tertiary container, a muted amber-brown: the intercalary band's fill in the dark scheme.
 * [DarkOnTertiaryContainer] on it: 7.4:1; [DarkOnSurfaceVariant] on it: 5.6:1.
 */
val DarkTertiaryContainer: Color = Color(0xFF6B3800)

/** Dark on-tertiary-container, the light peach. */
val DarkOnTertiaryContainer: Color = Color(0xFFFFDCBE)

/** Dark surface and background, near-black teal; [DarkOnSurface] on it: 14.2:1. */
val DarkSurface: Color = Color(0xFF0E1615)

/** Dark on-surface. */
val DarkOnSurface: Color = Color(0xFFDDE4E0)

/** Dark surface variant; [DarkOnSurfaceVariant] on it: 5.5:1. */
val DarkSurfaceVariant: Color = Color(0xFF3F4945)

/** Dark on-surface-variant; on [DarkSurface]: 10.8:1. */
val DarkOnSurfaceVariant: Color = Color(0xFFBEC9C4)

/** Dark outline; on [DarkSurface]: 5.8:1. */
val DarkOutline: Color = Color(0xFF889390)

/** Dark outline variant, for hairlines. */
val DarkOutlineVariant: Color = Color(0xFF3F4945)

/** Dark inverse surface. */
val DarkInverseSurface: Color = Color(0xFFDDE4E0)

/** Dark inverse on-surface; on [DarkInverseSurface]: 10.0:1. */
val DarkInverseOnSurface: Color = Color(0xFF2B3331)

/** Dark error (Material baseline); [DarkOnError] on it: 7.7:1. */
val DarkError: Color = Color(0xFFFFB4AB)

/** Dark on-error. */
val DarkOnError: Color = Color(0xFF690005)

/** Dark error container; [DarkOnErrorContainer] on it: 7.2:1. */
val DarkErrorContainer: Color = Color(0xFF93000A)

/** Dark on-error-container. */
val DarkOnErrorContainer: Color = Color(0xFFFFDAD6)

/** Dark surface container tiers: lowest (darkest) to highest. */
val DarkSurfaceContainerLowest: Color = Color(0xFF090F0E)

/** See [DarkSurfaceContainerLowest]. */
val DarkSurfaceContainerLow: Color = Color(0xFF161E1D)

/** See [DarkSurfaceContainerLowest]. */
val DarkSurfaceContainer: Color = Color(0xFF1A2322)

/** See [DarkSurfaceContainerLowest]. */
val DarkSurfaceContainerHigh: Color = Color(0xFF24302E)

/** See [DarkSurfaceContainerLowest]; [DarkOnSurface] on it: 9.0:1, [DarkPrimary]: 6.9:1. */
val DarkSurfaceContainerHighest: Color = Color(0xFF2F3B39)

/** Dark surface dim. */
val DarkSurfaceDim: Color = DarkSurface

/** Dark surface bright. */
val DarkSurfaceBright: Color = Color(0xFF333F3D)

/**
 * The brand light scheme, used below API 31 and whenever dynamic colour is off: teal primary on
 * cream, sage secondary, and a peach [ColorScheme.tertiaryContainer] that marks the intercalary band.
 */
val BrandLightColorScheme: ColorScheme =
    lightColorScheme(
        primary = LightPrimary,
        onPrimary = LightOnPrimary,
        primaryContainer = LightPrimaryContainer,
        onPrimaryContainer = LightOnPrimaryContainer,
        inversePrimary = LightInversePrimary,
        secondary = LightSecondary,
        onSecondary = LightOnSecondary,
        secondaryContainer = LightSecondaryContainer,
        onSecondaryContainer = LightOnSecondaryContainer,
        tertiary = LightTertiary,
        onTertiary = LightOnTertiary,
        tertiaryContainer = LightTertiaryContainer,
        onTertiaryContainer = LightOnTertiaryContainer,
        background = LightSurface,
        onBackground = LightOnSurface,
        surface = LightSurface,
        onSurface = LightOnSurface,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightOnSurfaceVariant,
        surfaceTint = LightPrimary,
        inverseSurface = LightInverseSurface,
        inverseOnSurface = LightInverseOnSurface,
        error = LightError,
        onError = LightOnError,
        errorContainer = LightErrorContainer,
        onErrorContainer = LightOnErrorContainer,
        outline = LightOutline,
        outlineVariant = LightOutlineVariant,
        scrim = Color.Black,
        surfaceBright = LightSurfaceBright,
        surfaceDim = LightSurfaceDim,
        surfaceContainer = LightSurfaceContainer,
        surfaceContainerHigh = LightSurfaceContainerHigh,
        surfaceContainerHighest = LightSurfaceContainerHighest,
        surfaceContainerLow = LightSurfaceContainerLow,
        surfaceContainerLowest = LightSurfaceContainerLowest,
    )

/**
 * The brand dark scheme: teal-mint primary on near-black teal, with the brand teal as the primary
 * container and a muted amber [ColorScheme.tertiaryContainer] for the intercalary band.
 */
val BrandDarkColorScheme: ColorScheme =
    darkColorScheme(
        primary = DarkPrimary,
        onPrimary = DarkOnPrimary,
        primaryContainer = DarkPrimaryContainer,
        onPrimaryContainer = DarkOnPrimaryContainer,
        inversePrimary = DarkInversePrimary,
        secondary = DarkSecondary,
        onSecondary = DarkOnSecondary,
        secondaryContainer = DarkSecondaryContainer,
        onSecondaryContainer = DarkOnSecondaryContainer,
        tertiary = DarkTertiary,
        onTertiary = DarkOnTertiary,
        tertiaryContainer = DarkTertiaryContainer,
        onTertiaryContainer = DarkOnTertiaryContainer,
        background = DarkSurface,
        onBackground = DarkOnSurface,
        surface = DarkSurface,
        onSurface = DarkOnSurface,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkOnSurfaceVariant,
        surfaceTint = DarkPrimary,
        inverseSurface = DarkInverseSurface,
        inverseOnSurface = DarkInverseOnSurface,
        error = DarkError,
        onError = DarkOnError,
        errorContainer = DarkErrorContainer,
        onErrorContainer = DarkOnErrorContainer,
        outline = DarkOutline,
        outlineVariant = DarkOutlineVariant,
        scrim = Color.Black,
        surfaceBright = DarkSurfaceBright,
        surfaceDim = DarkSurfaceDim,
        surfaceContainer = DarkSurfaceContainer,
        surfaceContainerHigh = DarkSurfaceContainerHigh,
        surfaceContainerHighest = DarkSurfaceContainerHighest,
        surfaceContainerLow = DarkSurfaceContainerLow,
        surfaceContainerLowest = DarkSurfaceContainerLowest,
    )
