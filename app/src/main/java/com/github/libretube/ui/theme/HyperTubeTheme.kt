package com.github.libretube.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.github.libretube.R

// iOS Dark Mode System Palette (Strictly static, NO Material You dynamic color)
val IosOledBlack = Color(0xFF000000)
val IosDeepBg = Color(0xFF090A0F)
val IosSurfacePrimary = Color(0xFF1C1C1E)
val IosSurfaceSecondary = Color(0xFF2C2C2E)
val IosSurfaceTertiary = Color(0xFF3A3A3C)
val IosSeparator = Color(0xFF38383A)

val IosElectricBlue = Color(0xFF0A84FF)
val IosIndigoAccent = Color(0xFF5E5CE6)
val IosCyanTint = Color(0xFF64D2FF)
val IosCrimsonPink = Color(0xFFFF375F)
val IosMintGreen = Color(0xFF30D158)
val IosAmberOrange = Color(0xFFFF9F0A)

val IosLabelPrimary = Color(0xFFFFFFFF)
val IosLabelSecondary = Color(0xFFEBEBF5).copy(alpha = 0.65f)
val IosLabelTertiary = Color(0xFFEBEBF5).copy(alpha = 0.38f)

val PlusJakartaFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium)
)

private val HyperTubeColorScheme = darkColorScheme(
    primary = IosElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF00366D),
    onPrimaryContainer = IosCyanTint,
    secondary = IosIndigoAccent,
    onSecondary = Color.White,
    secondaryContainer = IosSurfaceSecondary,
    onSecondaryContainer = IosLabelPrimary,
    tertiary = IosCrimsonPink,
    onTertiary = Color.White,
    background = IosOledBlack,
    onBackground = IosLabelPrimary,
    surface = IosSurfacePrimary,
    onSurface = IosLabelPrimary,
    surfaceVariant = IosSurfaceSecondary,
    onSurfaceVariant = IosLabelSecondary,
    outline = IosSeparator,
    error = Color(0xFFFF453A)
)

private val HyperTubeTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp
    ),
    titleSmall = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 15.sp
    )
)

@Composable
fun HyperTubeTheme(content: @Composable () -> Unit) {
    // Strictly iOS Dark Theme — no Material You dynamicColor
    MaterialTheme(
        colorScheme = HyperTubeColorScheme,
        typography = HyperTubeTypography,
        content = content
    )
}
