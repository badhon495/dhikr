package com.dhikr.app.core.model

data class Dhikr(
    val id: String,
    val name: String,
    val arabic: String,
    val transliteration: String,
    val translation: String,
    val lapTarget: Int,
    val lapCount: Int,
    val isFavorite: Boolean = false,
)
