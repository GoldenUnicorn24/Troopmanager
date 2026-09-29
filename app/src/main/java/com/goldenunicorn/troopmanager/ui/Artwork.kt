package com.goldenunicorn.troopmanager.ui

import com.goldenunicorn.troopmanager.R

fun portraitResource(key: String): Int = when (key) {
    "gold_elf" -> R.drawable.portrait_gold_elf
    "wood_elf" -> R.drawable.portrait_wood_elf
    "wall_guard" -> R.drawable.portrait_wall_guard
    "orc" -> R.drawable.portrait_orc
    "uruk" -> R.drawable.portrait_uruk
    "monster" -> R.drawable.portrait_monster
    "companion" -> R.drawable.portrait_companion
    else -> R.drawable.portrait_knight
}
