package com.goldenunicorn.troopmanager.data

import kotlinx.serialization.Serializable

@Serializable
data class SaveSlotMetadata(
    val slot: Int,
    val playerName: String,
    val day: Int,
    val title: String,
    val population: Int,
    val savedAt: Long,
    val ironman: Boolean = false,
)
