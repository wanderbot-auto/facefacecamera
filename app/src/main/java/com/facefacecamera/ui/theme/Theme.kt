package com.facefacecamera.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val FaceFaceColorScheme = darkColorScheme(
    primary = Peach,
    onPrimary = Ink,
    secondary = GlowBlue,
    background = Ink,
    onBackground = Cream,
    surface = InkSoft,
    onSurface = Cream,
)

@Composable
fun FaceFaceCameraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FaceFaceColorScheme,
        typography = FaceFaceTypography,
        content = content,
    )
}

