package com.dhikr.app.ui.theme

import androidx.compose.ui.graphics.Color

data class DhikrColorTokens(
    val bg: Color,
    val surface: Color,
    val card: Color,
    val text: Color,
    val dim: Color,
    val faint: Color,
    val line: Color,
    val sage: Color,
    val sageSoft: Color,
    val terra: Color,
    val terraSoft: Color,
    val track: Color,
    val onSage: Color,
)

val LightDhikrColors = DhikrColorTokens(
    bg = Color(0xFFF5EAD8),
    surface = Color(0xFFEBDDC5),
    card = Color(0xFFF9F4ED),
    text = Color(0xFF201E1D),
    dim = Color(0xFF645C50),
    faint = Color(0xFFA19786),
    line = Color(0x21201E1D), // rgba(32,30,29,.13)
    sage = Color(0xFF7A8A5E),
    sageSoft = Color(0xFFE1EECC),
    terra = Color(0xFFC67139),
    terraSoft = Color(0xFFFFE1D0),
    track = Color(0x1A201E1D), // rgba(32,30,29,.10)
    onSage = Color(0xFFF9F4ED),
)

val DarkDhikrColors = DhikrColorTokens(
    bg = Color(0xFF1C1A17),
    surface = Color(0xFF2A261F),
    card = Color(0xFF332E26),
    text = Color(0xFFF6EFE2),
    dim = Color(0xFFC0B6A5),
    faint = Color(0xFF82796A),
    line = Color(0x1FF6EFE2), // rgba(246,239,226,.12)
    sage = Color(0xFFAEBF92),
    sageSoft = Color(0xFF3D472B),
    terra = Color(0xFFF6A06B),
    terraSoft = Color(0xFF4D2F18),
    track = Color(0x1AF6EFE2), // rgba(246,239,226,.10)
    onSage = Color(0xFF272E1B),
)
