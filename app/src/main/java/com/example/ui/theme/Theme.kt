package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Immutable
data class ExtendedColors(
    val paid: Color,
    val paidContainer: Color,
    val partial: Color,
    val partialContainer: Color,
    val overdue: Color,
    val overdueContainer: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        paid = PaidLight,
        paidContainer = PaidContainerLight,
        partial = PartialLight,
        partialContainer = PartialContainerLight,
        overdue = OverdueLight,
        overdueContainer = OverdueContainerLight,
        heroStart = HeroCardStartLight,
        heroEnd = HeroCardEndLight,
        onHero = Color.White
    )
}

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimaryLight,
    onPrimary = OnEmeraldPrimaryLight,
    primaryContainer = EmeraldPrimaryContainerLight,
    onPrimaryContainer = OnEmeraldPrimaryContainerLight,
    secondary = GoldSecondaryLight,
    onSecondary = OnGoldSecondaryLight,
    secondaryContainer = GoldSecondaryContainerLight,
    onSecondaryContainer = OnGoldSecondaryContainerLight,
    tertiary = SlateTealTertiaryLight,
    onTertiary = OnSlateTealTertiaryLight,
    tertiaryContainer = SlateTealContainerLight,
    onTertiaryContainer = OnSlateTealContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight
)

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = OnEmeraldPrimaryDark,
    primaryContainer = EmeraldPrimaryContainerDark,
    onPrimaryContainer = OnEmeraldPrimaryContainerDark,
    secondary = GoldSecondaryDark,
    onSecondary = OnGoldSecondaryDark,
    secondaryContainer = GoldSecondaryContainerDark,
    onSecondaryContainer = OnGoldSecondaryContainerDark,
    tertiary = SlateTealTertiaryDark,
    onTertiary = OnSlateTealTertiaryDark,
    tertiaryContainer = SlateTealContainerDark,
    onTertiaryContainer = OnSlateTealContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

private val LightExtendedColors = ExtendedColors(
    paid = PaidLight,
    paidContainer = PaidContainerLight,
    partial = PartialLight,
    partialContainer = PartialContainerLight,
    overdue = OverdueLight,
    overdueContainer = OverdueContainerLight,
    heroStart = HeroCardStartLight,
    heroEnd = HeroCardEndLight,
    onHero = Color.White
)

private val DarkExtendedColors = ExtendedColors(
    paid = PaidDark,
    paidContainer = PaidContainerDark,
    partial = PartialDark,
    partialContainer = PartialContainerDark,
    overdue = OverdueDark,
    overdueContainer = OverdueContainerDark,
    heroStart = HeroCardStartDark,
    heroEnd = HeroCardEndDark,
    onHero = Color(0xFFEAF7F3)
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun InstallmentManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors,
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
