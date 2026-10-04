package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

@Serializable
enum class BattleTerrain(val label: String, val explanation: String) {
    PLAIN("Ebene", "Kavallerie hat Raum für einen vollen Angriff."),
    FOREST("Wald", "Reiter und Artillerie werden gebremst; Waldelben kämpfen besser."),
    HILL("Anhöhe", "Fernkämpfer erhalten Sicht und Reichweite."),
    RIVER("Fluss", "Ein Übergang bremst Angreifer und schützt Verteidiger."),
    BRIDGE("Brücke", "Ein schmaler Übergang schützt eine kleine Verteidigung."),
    PASS("Bergpass", "Schwere Infanterie hält den Engpass; Kavallerie wird gebremst."),
    MUD("Schlamm", "Schwere Reiter und Artillerie verlieren Beweglichkeit."),
    WALL("Festungsmauer", "Fernkampf und disziplinierte Wachen profitieren."),
    STREET("Stadtstraße", "Kurze Sicht und enge Straßen schwächen Reiter."),
}

@Serializable
enum class MoraleState(val label: String) {
    EUPHORIC("Euphorisch"), STABLE("Stabil"), NERVOUS("Nervös"), WAVERING("Wankend"), PANICKED("Panisch"), ROUTING("Fliehend");
    companion object {
        fun from(value: Int, routed: Boolean = false): MoraleState = when {
            routed || value < 15 -> ROUTING
            value < 25 -> PANICKED
            value < 40 -> WAVERING
            value < 60 -> NERVOUS
            value < 90 -> STABLE
            else -> EUPHORIC
        }
    }
}

@Serializable
enum class BattleParticipation(val label: String) { COMMAND("Vom Feldlager führen"), PERSONAL("Selbst in die Schlacht ziehen") }

@Serializable
enum class CombatantStatus(val label: String) { ACTIVE("Einsatzbereit"), WOUNDED("Verwundet"), UNCONSCIOUS("Bewusstlos"), CAPTURED("Gefangen"), DEAD("Gefallen") }

@Serializable
data class CommanderCondition(val commanderId: Long, val status: CombatantStatus, val untilDay: Int = 0)

@Serializable
enum class EquipmentQuality(val label: String, val power: Double) {
    IMPROVISED("Improvisiert", 0.90), NORMAL("Normal", 1.0), GOOD("Gut", 1.07), MASTERWORK("Meisterlich", 1.14), LEGENDARY("Legendär", 1.22),
}

@Serializable
data class EquipmentBatch(val type: UnitType, val quality: EquipmentQuality = EquipmentQuality.NORMAL, val stock: Int = 0)

@Serializable
data class WoundedCohort(val id: String, val type: UnitType, val soldiers: Int, val recoveryDay: Int, val experience: Int = 0, val equipment: Int = 60)

@Serializable
data class CaptiveGroup(val id: String, val enemy: EnemyType, val soldiers: Int, val own: Boolean = false, val type: UnitType? = null, val capturedDay: Int = 0, val factionId: String? = null)

@Serializable
data class CasualtyReport(val dead: Int = 0, val wounded: Int = 0, val missing: Int = 0, val captured: Int = 0) {
    val total: Int get() = dead + wounded + missing + captured
}

@Serializable
enum class CaptiveAction(val label: String) { RELEASE("Freilassen"), RANSOM("Lösegeld"), EXCHANGE("Gefangenenaustausch"), RECRUIT("Freiwillige Aufnahme") }

@Serializable
data class EliteUnit(val id: String, val name: String, val type: UnitType, val soldiers: Int, val battles: Int = 0, val victories: Int = 0, val banner: String = "Sonne", val traits: List<String> = emptyList(), val history: List<String> = emptyList())

/** Compact, non-recursive combat context. Replays run the same BattleEngine exchanges. */
@Serializable
data class BattleReplayStart(
    val player: CharacterProfile,
    val commanders: List<Commander>,
    val realm: Realm,
    val resources: Resources,
    val armyPools: List<ArmyUnitPool>,
    val population: Population,
    val assignments: List<CommanderAssignment>,
    val initialContingents: List<BattleContingent>,
    val initialFronts: List<BattleFront>,
    val devices: List<SiegeDevice>,
    val day: Int,
    val equipment: List<EquipmentBatch> = emptyList(),
    val heroPerk: Boolean = false,
    val reservePerk: Boolean = false,
    val feignedRetreatPerk: Boolean = false,
    val rallyPerk: Boolean = false,
    val moraleBonus: Int = 0,
    val seasonPenalty: Double = 1.0,
    val rangedWeather: Double = 1.0,
    val cavalryWeather: Double = 1.0,
    val healingBonus: Int = 0,
    val buildingDamage: Map<BuildingType, Int> = emptyMap(),
    val gateReinforcement: Int = 0,
    val eliteUnits: List<EliteUnit> = emptyList(),
    val permadeath: Boolean = false,
    val enemyFortification: Int = 0,
    val location: String? = null,
    val deployedMorale: Int? = null,
    val enemyExperience: Int = 0,
    val doctrine: MilitaryDoctrine = MilitaryDoctrine.BALANCED,
    val rangedSupplyFactor: Double = 1.0,
    val frontier: FrontierState = FrontierState(),
    val companion: CompanionProfile = CompanionProfile(),
    val combatVersion: Int = 1,
    val initialSegments: List<FortificationSegmentState> = emptyList(),
    val initialSiegeDevices: List<SiegeDeviceState> = emptyList(),
    val initialEnemyRoster: List<EnemyBattleUnit> = emptyList(),
    val arrowsLoaded: Int = -1,
    val enemyArrowsLoaded: Int = -1,
    val personalSection: BattleSection = BattleSection.CENTER,
    val militaryStock: MilitaryStock = MilitaryStock(),
    val research: ResearchState = ResearchState(),
    val artilleryLoaded: Int = -1,
    val enemyArtilleryLoaded: Int = -1,
    val counterTunnelUnlocked: Boolean = false,
)

@Serializable
data class BattleInput(val decision: BattleDecision? = null, val event: BattleEvent? = null)

@Serializable
data class BattleRecord(
    val id: String,
    val day: Int,
    val place: String,
    val enemy: EnemyType,
    val seed: Int,
    val tactic: Tactic,
    val ownStart: Int,
    val enemyStart: Int,
    val ownRemaining: Int,
    val enemyRemaining: Int,
    val victory: Boolean,
    val minute: Int,
    val casualties: CasualtyReport,
    val commanders: List<Long> = emptyList(),
    val turningPoints: List<String> = emptyList(),
    val terrain: Map<BattleSection, BattleTerrain> = emptyMap(),
    val participation: BattleParticipation = BattleParticipation.COMMAND,
    val replay: BattleReplayStart? = null,
    val inputs: List<BattleInput> = emptyList(),
    val enemyFactionName: String? = null,
    val enemyUnits: List<UnitAllocation> = emptyList(),
    val enemyFactionId: String? = null,
    val enemyArmyName: String? = null,
    val outcomeGrade: BattleOutcomeGrade? = null,
    val segments: List<FortificationSegmentState> = emptyList(),
    val siegeDevices: List<SiegeDeviceState> = emptyList(),
    val exchanges: List<BattleExchangeReport> = emptyList(),
    val arrowsUsed: Int = 0,
    val weaponChargesUsed: Map<WallWeaponType, Int> = emptyMap(),
    val aftermath: List<String> = emptyList(),
)

@Serializable
data class WarState(
    val wounded: List<WoundedCohort> = emptyList(),
    val captives: List<CaptiveGroup> = emptyList(),
    val buildingDamage: Map<BuildingType, Int> = emptyMap(),
    val equipment: List<EquipmentBatch> = emptyList(),
    val commanderConditions: List<CommanderCondition> = emptyList(),
    val playerCondition: CombatantStatus = CombatantStatus.ACTIVE,
    val playerRecoveryDay: Int = 0,
    val eliteUnits: List<EliteUnit> = emptyList(),
    val history: List<BattleRecord> = emptyList(),
    val siegeFoodStored: Int = 0,
    val civiliansEvacuated: Boolean = false,
    val gateReinforcement: Int = 0,
    val lastTickDay: Int = 0,
) {
    fun unavailableCommander(id: Long): Boolean = commanderConditions.any { it.commanderId == id && it.status != CombatantStatus.ACTIVE }
}

/** Rendering projection; allocation is capped at 300 regardless of soldier count. */
data class VisualBattleGroup(val section: BattleSection, val type: UnitType?, val soldiers: Int, val enemy: Boolean, val routed: Boolean, val commanderId: Long? = null)
