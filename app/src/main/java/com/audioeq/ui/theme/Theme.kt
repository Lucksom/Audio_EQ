package com.audioeq.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AudioEqTheme(
    themeMode: String = "light",
    seedHex: String = "#006a6a",
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val resolvedDark = when (themeMode.lowercase()) {
        "light" -> false
        "dark" -> true
        else -> darkTheme
    }

    val palette = remember(seedHex, resolvedDark) {
        MonetEngine.buildPalette(seedHex = seedHex, dark = resolvedDark)
    }

    val colorScheme = remember(palette) {
        MonetEngine.toMaterialColorScheme(palette)
    }

    CompositionLocalProvider(LocalMonetPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    AudioEqTheme(
        themeMode = if (darkTheme) "dark" else "light",
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        content = content
    )
}
