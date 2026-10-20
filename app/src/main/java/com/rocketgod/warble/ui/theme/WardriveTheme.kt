package com.rocketgod.warble.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rocketgod.warble.ui.Palette

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "System"), DARK("dark", "Dark"), LIGHT("light", "Light");
    companion object { fun of(key: String?) = entries.firstOrNull { it.key == key } ?: DARK }
}

/**
 * Reserved status colors for risk / alert states (never reused as series colors). Always
 * shown together with an icon and a text label so color is never the only signal.
 */
@Immutable
data class StatusColors(val good: Color, val warning: Color, val serious: Color, val critical: Color, val neutral: Color)

val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(Color(0xFF0CA30C), Color(0xFFFAB219), Color(0xFFEC835A), Color(0xFFD03B3B), Color(0xFF898781))
}

/** True when the active scheme is dark; legacy code and map tiles use it. */
val LocalIsDarkTheme = staticCompositionLocalOf { true }

/** Monospace for technical identifiers (MACs, UUIDs, hashes) only. */
val CodeFont: FontFamily = FontFamily.Monospace

/**
 * MD3 type scale on the system sans. Slightly tighter line heights than the defaults suit the
 * data-dense screens; display / headline sizes follow the M3 spec.
 */
private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Extra style for monospaced identifiers, sized like bodySmall. */
val CodeStyle = TextStyle(fontFamily = CodeFont, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * App theme. [seed] is the user's chosen accent (the legacy "skin"), from which a full tonal
 * scheme is generated; [dynamicColor] uses Material You wallpaper colors on Android 12+.
 */
@Composable
fun WardriveTheme(
    seed: Color,
    mode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val ctx = LocalContext.current
    val scheme: ColorScheme = remember(seed, dark, dynamicColor) {
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        } else TonalScheme.scheme(seed, dark)
    }
    // Legacy screens read colors from the Palette object; keep it in sync with the scheme.
    SideEffect { Palette.applyScheme(scheme, dark) }

    androidx.compose.runtime.CompositionLocalProvider(LocalIsDarkTheme provides dark) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
