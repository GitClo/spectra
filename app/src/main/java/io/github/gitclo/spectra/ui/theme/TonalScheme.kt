package io.github.gitclo.spectra.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * Builds a complete Material 3 [ColorScheme] from a single seed color.
 *
 * Material's reference implementation uses the HCT color space; this is a compact
 * approximation in OKLCH, which is perceptually uniform enough for UI tones. Each MD3 "tone"
 * (0 = black, 100 = white, defined on CIE L*) is converted to OKLab lightness, and each key
 * palette keeps the seed's hue with a fixed chroma budget:
 *
 *  - primary:   seed hue, seed chroma (capped)
 *  - secondary: seed hue, low chroma
 *  - tertiary:  hue + 60°, medium chroma
 *  - neutral / neutral-variant: seed hue, near-zero chroma (tinted greys)
 *
 * Colors outside sRGB are brought in by reducing chroma, which preserves hue and tone.
 */
object TonalScheme {

    private data class Lch(val l: Double, val c: Double, val h: Double)

    private class Palette(private val hue: Double, private val chroma: Double) {
        fun tone(t: Int): Color = fromLch(toneToOkL(t.toDouble()), chroma, hue)
    }

    fun scheme(seed: Color, dark: Boolean): ColorScheme {
        val s = toLch(seed)
        // Achromatic seeds (white / grey skins) get a gently tinted neutral scheme.
        val seedChroma = s.c.coerceIn(0.0, 0.16)
        val primary = Palette(s.h, seedChroma.coerceAtLeast(0.02))
        val secondary = Palette(s.h, (seedChroma / 3).coerceAtMost(0.05))
        val tertiary = Palette((s.h + 60) % 360, (seedChroma * 0.6).coerceAtLeast(0.02))
        val neutral = Palette(s.h, (seedChroma / 12).coerceAtMost(0.012))
        val neutralVariant = Palette(s.h, (seedChroma / 6).coerceAtMost(0.025))
        val error = Palette(25.0, 0.17)

        return if (dark) darkColorScheme(
            primary = primary.tone(80), onPrimary = primary.tone(20),
            primaryContainer = primary.tone(30), onPrimaryContainer = primary.tone(90),
            inversePrimary = primary.tone(40),
            secondary = secondary.tone(80), onSecondary = secondary.tone(20),
            secondaryContainer = secondary.tone(30), onSecondaryContainer = secondary.tone(90),
            tertiary = tertiary.tone(80), onTertiary = tertiary.tone(20),
            tertiaryContainer = tertiary.tone(30), onTertiaryContainer = tertiary.tone(90),
            error = error.tone(80), onError = error.tone(20),
            errorContainer = error.tone(30), onErrorContainer = error.tone(90),
            background = neutral.tone(6), onBackground = neutral.tone(90),
            surface = neutral.tone(6), onSurface = neutral.tone(90),
            surfaceVariant = neutralVariant.tone(30), onSurfaceVariant = neutralVariant.tone(80),
            surfaceTint = primary.tone(80),
            inverseSurface = neutral.tone(90), inverseOnSurface = neutral.tone(20),
            outline = neutralVariant.tone(60), outlineVariant = neutralVariant.tone(30),
            scrim = Color.Black,
            surfaceBright = neutral.tone(24), surfaceDim = neutral.tone(6),
            surfaceContainerLowest = neutral.tone(4), surfaceContainerLow = neutral.tone(10),
            surfaceContainer = neutral.tone(12), surfaceContainerHigh = neutral.tone(17),
            surfaceContainerHighest = neutral.tone(22),
        ) else lightColorScheme(
            primary = primary.tone(40), onPrimary = primary.tone(100),
            primaryContainer = primary.tone(90), onPrimaryContainer = primary.tone(10),
            inversePrimary = primary.tone(80),
            secondary = secondary.tone(40), onSecondary = secondary.tone(100),
            secondaryContainer = secondary.tone(90), onSecondaryContainer = secondary.tone(10),
            tertiary = tertiary.tone(40), onTertiary = tertiary.tone(100),
            tertiaryContainer = tertiary.tone(90), onTertiaryContainer = tertiary.tone(10),
            error = error.tone(40), onError = error.tone(100),
            errorContainer = error.tone(90), onErrorContainer = error.tone(10),
            background = neutral.tone(98), onBackground = neutral.tone(10),
            surface = neutral.tone(98), onSurface = neutral.tone(10),
            surfaceVariant = neutralVariant.tone(90), onSurfaceVariant = neutralVariant.tone(30),
            surfaceTint = primary.tone(40),
            inverseSurface = neutral.tone(20), inverseOnSurface = neutral.tone(95),
            outline = neutralVariant.tone(50), outlineVariant = neutralVariant.tone(80),
            scrim = Color.Black,
            surfaceBright = neutral.tone(98), surfaceDim = neutral.tone(87),
            surfaceContainerLowest = neutral.tone(100), surfaceContainerLow = neutral.tone(96),
            surfaceContainer = neutral.tone(94), surfaceContainerHigh = neutral.tone(92),
            surfaceContainerHighest = neutral.tone(90),
        )
    }

    // ---- color math -----------------------------------------------------------------------

    /** MD3 tone is CIE L*; for a neutral, OKLab L equals cbrt(Y). */
    private fun toneToOkL(t: Double): Double {
        val y = if (t > 8.0) ((t + 16.0) / 116.0).pow(3) else t / 903.3
        return cbrt(y)
    }

    private fun srgbToLinear(v: Double) = if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    private fun linearToSrgb(v: Double) = if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055

    private fun toLch(c: Color): Lch {
        val r = srgbToLinear(c.red.toDouble()); val g = srgbToLinear(c.green.toDouble()); val b = srgbToLinear(c.blue.toDouble())
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        val L = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
        val A = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
        val B = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        val h = Math.toDegrees(atan2(B, A)).let { if (it < 0) it + 360 else it }
        return Lch(L, hypot(A, B), h)
    }

    /** OKLCH -> sRGB, reducing chroma until the color is in gamut. */
    private fun fromLch(l: Double, c0: Double, h: Double): Color {
        var c = c0
        repeat(24) {
            val rgb = lchToRgb(l, c, h)
            if (rgb.all { it in -0.0005..1.0005 }) return Color(rgb[0].toFloat().coerceIn(0f, 1f), rgb[1].toFloat().coerceIn(0f, 1f), rgb[2].toFloat().coerceIn(0f, 1f))
            c *= 0.85
        }
        val rgb = lchToRgb(l, 0.0, h)
        return Color(rgb[0].toFloat().coerceIn(0f, 1f), rgb[1].toFloat().coerceIn(0f, 1f), rgb[2].toFloat().coerceIn(0f, 1f))
    }

    private fun lchToRgb(L: Double, c: Double, h: Double): DoubleArray {
        val a = c * cos(Math.toRadians(h)); val b = c * sin(Math.toRadians(h))
        val l = (L + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m = (L - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s = (L - 0.0894841775 * a - 1.2914855480 * b).pow(3)
        val r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        val g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        val bb = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        return doubleArrayOf(linearToSrgb(r), linearToSrgb(g), linearToSrgb(bb))
    }
}
