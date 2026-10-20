package com.rocketgod.warble.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.rocketgod.warble.model.Skin
import com.rocketgod.warble.ui.theme.TonalScheme
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every accent skin must yield an MD3 scheme whose text roles meet WCAG contrast. */
class TonalSchemeTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05; val lb = b.luminance() + 0.05
        return maxOf(la, lb).toDouble() / minOf(la, lb)
    }

    private fun check(name: String, fg: Color, bg: Color, min: Double) {
        val c = contrast(fg, bg)
        assertTrue("$name contrast %.2f < $min".format(c), c >= min)
    }

    @Test fun `all skins produce accessible schemes in both modes`() {
        for (skin in Skin.entries) for (dark in listOf(true, false)) {
            val cs = TonalScheme.scheme(Color(skin.accentHex), dark)
            val tag = "${skin.name}/${if (dark) "dark" else "light"}"
            check("$tag onSurface", cs.onSurface, cs.surface, 7.0)
            check("$tag onSurfaceVariant", cs.onSurfaceVariant, cs.surface, 4.5)
            check("$tag onSurfaceVariant/container", cs.onSurfaceVariant, cs.surfaceContainerHigh, 4.5)
            check("$tag onPrimary", cs.onPrimary, cs.primary, 4.5)
            check("$tag onPrimaryContainer", cs.onPrimaryContainer, cs.primaryContainer, 4.5)
            check("$tag onSecondaryContainer", cs.onSecondaryContainer, cs.secondaryContainer, 4.5)
            check("$tag primary on surface", cs.primary, cs.surface, 3.0)
            check("$tag onError", cs.onError, cs.error, 4.5)
            // Surface containers must step monotonically so cards are distinguishable.
            val steps = listOf(cs.surfaceContainerLowest, cs.surfaceContainerLow, cs.surfaceContainer, cs.surfaceContainerHigh, cs.surfaceContainerHighest)
                .map { it.luminance() }
            val ordered = if (dark) steps.zipWithNext().all { (a, b) -> b >= a } else steps.zipWithNext().all { (a, b) -> b <= a }
            assertTrue("$tag surface containers not monotonic: $steps", ordered)
        }
    }
}
