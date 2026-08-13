package com.abdulkus.essentialremap.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NothingLightColors = lightColorScheme(
    primary = Color(0xFFD71920),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD9),
    onPrimaryContainer = Color(0xFF410005),
    background = Color(0xFFF2F2EF),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFE8E8E4),
    onSurfaceVariant = Color(0xFF62625E),
    outline = Color(0xFFB7B7B1),
    error = Color(0xFFBA1A1A),
)

private val NothingDarkColors = darkColorScheme(
    primary = Color(0xFFFF5B5F),
    onPrimary = Color(0xFF4B0007),
    primaryContainer = Color(0xFF68000C),
    onPrimaryContainer = Color(0xFFFFDAD9),
    background = Color(0xFF0D0D0D),
    onBackground = Color(0xFFF2F2EE),
    surface = Color(0xFF171717),
    onSurface = Color(0xFFF2F2EE),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFBDBDB7),
    outline = Color(0xFF5D5D59),
    error = Color(0xFFFFB4AB),
)

private val NothingShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val NothingTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
    )
}

@Composable
fun EssentialRemapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) NothingDarkColors else NothingLightColors,
        shapes = NothingShapes,
        typography = NothingTypography,
        content = content,
    )
}
