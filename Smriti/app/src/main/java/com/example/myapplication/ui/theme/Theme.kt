package com.example.myapplication.ui.theme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val SmritiColors = lightColorScheme(
    primary = Color(0xFF6952C5), onPrimary = Color.White,
    primaryContainer = Color(0xFFF0EBFF), onPrimaryContainer = Color(0xFF49348D),
    secondary = Color(0xFF468D79), secondaryContainer = Color(0xFFEAF6EF),
    tertiary = Color(0xFF4B95B7), background = Color(0xFFFAFCFC),
    surface = Color(0xFFFFFEFB), onSurface = Color(0xFF233449),
    onBackground = Color(0xFF233449), surfaceVariant = Color(0xFFF3F1F8),
    onSurfaceVariant = Color(0xFF66758A), outline = Color(0xFFBFC8D2),
    outlineVariant = Color(0xFFE1E7EC)
)
@Composable
fun MyApplicationTheme(darkTheme: Boolean = false, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SmritiColors, typography = Typography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp)), content = content)
}
