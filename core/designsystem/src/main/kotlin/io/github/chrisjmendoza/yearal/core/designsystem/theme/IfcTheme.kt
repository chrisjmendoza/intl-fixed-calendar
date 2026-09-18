package io.github.chrisjmendoza.yearal.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * The app's Material 3 theme: dynamic colour on API 31 and later, the Material baseline palette below
 * that until a brand seed colour is chosen (docs/ROADMAP.md open decision #10; docs/ARCHITECTURE.md
 * "Decisions at a glance").
 *
 * @param darkTheme whether to use the dark scheme; defaults to the system setting.
 * @param dynamicColor whether to derive the scheme from the wallpaper where the platform supports it.
 * Previews and screenshot tests pass `false` so goldens are deterministic.
 */
@Composable
fun IfcTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> {
                darkColorScheme()
            }

            else -> {
                lightColorScheme()
            }
        }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
