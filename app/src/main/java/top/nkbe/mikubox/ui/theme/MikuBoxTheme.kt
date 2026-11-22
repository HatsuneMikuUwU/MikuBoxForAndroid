package top.nkbe.mikubox.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.AttrRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.utils.Theme

@Immutable
data class MikuExtendedColors(
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val success: Color,
    val softPanel: Color,
)

private val LightMikuExtended = MikuExtendedColors(
    warning = Color(0xFFD97706),
    onWarning = Color.White,
    warningContainer = Color(0xFFFEF3C7),
    success = Color(0xFF00897B),
    softPanel = Color(0xFFF0F9F8),
)

private val DarkMikuExtended = MikuExtendedColors(
    warning = Color(0xFFFBBF24),
    onWarning = Color(0xFF1F1600),
    warningContainer = Color(0xFF3A2A06),
    success = Color(0xFF4DD0C4),
    softPanel = Color(0xFF132220),
)

val LocalMikuExtendedColors = staticCompositionLocalOf { LightMikuExtended }

val MikuShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val DefaultMikuLightColors = lightColorScheme(
    primary = Color(0xFF006B61),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9DF2E5),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E3),
    onSecondaryContainer = Color(0xFF05201D),
    surface = Color(0xFFF4FBF9),
    onSurface = Color(0xFF161D1C),
    surfaceVariant = Color(0xFFDAE5E2),
    onSurfaceVariant = Color(0xFF3F4947),
)

private val DefaultMikuDarkColors = darkColorScheme(
    primary = Color(0xFF67D9C9),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFF9DF2E5),
    secondary = Color(0xFFB1CCC7),
    onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF324B47),
    onSecondaryContainer = Color(0xFFCCE8E3),
    surface = Color(0xFF0E1514),
    onSurface = Color(0xFFDDE4E2),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
)

/**
 * Bridges MikuBox's active Material3Expressive XML theme (22 accent themes, Material You dynamic,
 * dark mode, and AMOLED True Black) into Jetpack Compose so XML and Compose screens stay 100% consistent.
 */
@Composable
fun MikuBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    trueBlack: Boolean = remember { DataStore.trueBlackEnabled },
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val appTheme = remember { DataStore.appTheme }

    val colorScheme = remember(context, darkTheme, trueBlack, appTheme) {
        val base = if (appTheme == Theme.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            resolveColorSchemeFromHostTheme(
                context = context,
                fallback = if (darkTheme) DefaultMikuDarkColors else DefaultMikuLightColors,
            )
        }

        if (darkTheme && trueBlack) {
            base.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = Color(0xFF0A0A0A),
                surfaceContainer = Color(0xFF121212),
                surfaceContainerHigh = Color(0xFF181818),
                surfaceContainerHighest = Color(0xFF202020),
            )
        } else {
            base
        }
    }

    val extended = remember(darkTheme, trueBlack, colorScheme) {
        val baseExt = if (darkTheme) DarkMikuExtended else LightMikuExtended
        if (darkTheme && trueBlack) {
            baseExt.copy(softPanel = Color(0xFF101817))
        } else {
            baseExt.copy(
                softPanel = colorScheme.primaryContainer.copy(alpha = if (darkTheme) 0.22f else 0.35f),
            )
        }
    }

    CompositionLocalProvider(LocalMikuExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = MikuShapes,
            typography = Typography(),
            content = content,
        )
    }
}

private fun resolveColorSchemeFromHostTheme(
    context: Context,
    fallback: ColorScheme,
): ColorScheme {
    fun attr(@AttrRes id: Int, defaultColor: Color): Color {
        val resolved = MaterialColors.getColor(context, id, defaultColor.toArgb())
        return Color(resolved)
    }

    return fallback.copy(
        primary = attr(MaterialR.attr.colorPrimary, fallback.primary),
        onPrimary = attr(MaterialR.attr.colorOnPrimary, fallback.onPrimary),
        primaryContainer = attr(MaterialR.attr.colorPrimaryContainer, fallback.primaryContainer),
        onPrimaryContainer = attr(MaterialR.attr.colorOnPrimaryContainer, fallback.onPrimaryContainer),
        secondary = attr(MaterialR.attr.colorSecondary, fallback.secondary),
        onSecondary = attr(MaterialR.attr.colorOnSecondary, fallback.onSecondary),
        secondaryContainer = attr(MaterialR.attr.colorSecondaryContainer, fallback.secondaryContainer),
        onSecondaryContainer = attr(MaterialR.attr.colorOnSecondaryContainer, fallback.onSecondaryContainer),
        tertiary = attr(MaterialR.attr.colorTertiary, fallback.tertiary),
        onTertiary = attr(MaterialR.attr.colorOnTertiary, fallback.onTertiary),
        tertiaryContainer = attr(MaterialR.attr.colorTertiaryContainer, fallback.tertiaryContainer),
        onTertiaryContainer = attr(MaterialR.attr.colorOnTertiaryContainer, fallback.onTertiaryContainer),
        surface = attr(MaterialR.attr.colorSurface, fallback.surface),
        onSurface = attr(MaterialR.attr.colorOnSurface, fallback.onSurface),
        surfaceVariant = attr(MaterialR.attr.colorSurfaceVariant, fallback.surfaceVariant),
        onSurfaceVariant = attr(MaterialR.attr.colorOnSurfaceVariant, fallback.onSurfaceVariant),
        surfaceContainerLowest = attr(MaterialR.attr.colorSurfaceContainerLowest, fallback.surfaceContainerLowest),
        surfaceContainerLow = attr(MaterialR.attr.colorSurfaceContainerLow, fallback.surfaceContainerLow),
        surfaceContainer = attr(MaterialR.attr.colorSurfaceContainer, fallback.surfaceContainer),
        surfaceContainerHigh = attr(MaterialR.attr.colorSurfaceContainerHigh, fallback.surfaceContainerHigh),
        surfaceContainerHighest = attr(MaterialR.attr.colorSurfaceContainerHighest, fallback.surfaceContainerHighest),
        outline = attr(MaterialR.attr.colorOutline, fallback.outline),
        outlineVariant = attr(MaterialR.attr.colorOutlineVariant, fallback.outlineVariant),
        error = attr(MaterialR.attr.colorError, fallback.error),
        onError = attr(MaterialR.attr.colorOnError, fallback.onError),
    )
}

object MikuTheme {
    val extendedColors: MikuExtendedColors
        @Composable get() = LocalMikuExtendedColors.current
}
