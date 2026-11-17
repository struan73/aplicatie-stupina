package ro.stupina.offline.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Paletă de contrast ridicat pentru lizibilitate excelentă în lumina puternică de afară
val HoneyAmber = Color(0xFFF59E0B)
val CharcoalInk = Color(0xFF181613)
val SunlitCanvas = Color(0xFFFAF8F4)
val WarmSurface = Color(0xFFFFFFFF)
val StoneBorder = Color(0xFFE2DDD3)
val AlertRed = Color(0xFFB91C1C)
val HealthyGreen = Color(0xFF15803D)

private val StupinaColorScheme = lightColorScheme(
    primary = HoneyAmber,
    onPrimary = CharcoalInk,
    secondary = CharcoalInk,
    onSecondary = SunlitCanvas,
    background = SunlitCanvas,
    onBackground = CharcoalInk,
    surface = WarmSurface,
    onSurface = CharcoalInk,
    surfaceVariant = Color(0xFFF2EFE8),
    onSurfaceVariant = Color(0xFF4A453D),
    outline = StoneBorder,
    error = AlertRed,
    onError = Color.White
)

private val StupinaTypography = Typography(
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CharcoalInk),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CharcoalInk),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = CharcoalInk),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, color = CharcoalInk),
    bodyMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, color = Color(0xFF4A453D)),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CharcoalInk)
)

@Composable
fun StupinaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StupinaColorScheme,
        typography = StupinaTypography,
        content = content
    )
}
