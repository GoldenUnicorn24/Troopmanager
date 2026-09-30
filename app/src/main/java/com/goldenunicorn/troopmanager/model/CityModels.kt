package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

@Serializable
enum class WorkerPriority(val label: String) {
    BALANCED("Ausgewogen"),
    FOOD("Nahrung"),
    WOOD("Holz"),
    STONE("Stein"),
    IRON("Eisen"),
    TRADE("Handel"),
}

@Serializable
enum class TaxLevel(val label: String, val goldFactor: Double, val growthFactor: Double) {
    LOW("Niedrig", 0.8, 1.25),
    NORMAL("Normal", 1.0, 1.0),
    HIGH("Hoch", 1.25, 0.65),
}

@Serializable
enum class Season(val label: String) {
    SUMMER("Sommer"),
    AUTUMN("Herbst"),
    WINTER("Winter"),
}

@Serializable
enum class ResourceKind(val label: String) {
    GOLD("Gold"),
    FOOD("Nahrung"),
    WOOD("Holz"),
    STONE("Stein"),
    IRON("Eisen");

    fun value(resources: Resources): Int =
        when (this) {
            GOLD -> resources.gold
            FOOD -> resources.food
            WOOD -> resources.wood
            STONE -> resources.stone
            IRON -> resources.iron
        }

    fun withValue(resources: Resources, value: Int): Resources =
        when (this) {
            GOLD -> resources.copy(gold = value)
            FOOD -> resources.copy(food = value)
            WOOD -> resources.copy(wood = value)
            STONE -> resources.copy(stone = value)
            IRON -> resources.copy(iron = value)
        }
}

data class ResourceBreakdown(
    val base: Int,
    val building: Int,
    val territory: Int,
    val eventBonus: Int,
    val workerFactor: Double,
    val taxBonus: Int,
    val gross: Int,
    val upkeep: Int,
    val net: Int,
    val capacity: Int,
    val overflow: Int,
)

@Serializable
data class ConstructionOrder(
    val id: Long,
    val type: BuildingType,
    val targetLevel: Int,
    val daysRemaining: Int,
    val totalDays: Int,
)

@Serializable
data class CityState(
    val housingCapacity: Int = 2400,
    val storageCapacity: Resources = Resources(20000, 24000, 16000, 16000, 12000),
    val workerPriority: WorkerPriority = WorkerPriority.BALANCED,
    val taxLevel: TaxLevel = TaxLevel.NORMAL,
    val satisfaction: Int = 50,
    val prosperity: Int = 50,
    val security: Int = 50,
    val constructionQueue: List<ConstructionOrder> = emptyList(),
    val lastCompletedBuildings: List<BuildingType> = emptyList(),
    val season: Season = Season.SUMMER,
)
