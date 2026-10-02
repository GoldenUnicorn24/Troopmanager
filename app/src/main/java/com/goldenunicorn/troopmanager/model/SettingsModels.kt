package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

@Serializable
enum class RomanceMode(val label: String) {
    OFF("Aus"), ROMANCE("Romantik"), MATURE("Reife Romanze · Fade-to-black")
}

@Serializable
enum class Difficulty(val label: String) {
    STORY("Geschichte"), STANDARD("Standard"), VETERAN("Veteran")
}

@Serializable
data class GameSettings(
    val sound: Boolean = true,
    val music: Boolean = true,
    val haptics: Boolean = true,
    val animations: Boolean = true,
    val battleSpeed: Float = 1f,
    val textScale: Float = 1f,
    val romance: RomanceMode = RomanceMode.OFF,
    val dynasty: Boolean = false,
    val difficulty: Difficulty = Difficulty.STANDARD,
    val ironman: Boolean = false,
    val permadeath: Boolean = false,
)
