package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

/** Typed extension points; future research is deliberately separate from character skill points. */
@Serializable
enum class ResearchBranch(val label: String) {
    AGRICULTURE("Landwirtschaft"),
    ENGINEERING("Ingenieurswesen"),
    LOGISTICS("Logistik"),
    DIPLOMACY("Staatskunst"),
}

data class ResearchDefinition(
    val id: String,
    val branch: ResearchBranch,
    val prerequisite: String?,
    val building: BuildingType,
    val cost: Resources,
    val days: Int,
)

enum class EquipmentSlot {
    WEAPON,
    ARMOR,
    MOUNT,
    SIEGE_ENGINE,
}

data class EquipmentDefinition(
    val id: String,
    val slot: EquipmentSlot,
    val requiredBuilding: BuildingType,
    val cost: Resources,
)

enum class SoundCue {
    UI_SELECT,
    CONSTRUCTION_COMPLETE,
    MISSION_RETURN,
    BATTLE_VOLLEY,
    INVASION_WARNING,
}

/** Android sound implementation can be injected without changing campaign/save logic. */
fun interface GameAudio {
    fun play(cue: SoundCue)
}

object SilentGameAudio : GameAudio {
    override fun play(cue: SoundCue) = Unit
}

@Serializable
enum class CommanderEventKind {
    RIVALRY,
    FRIENDSHIP,
    PROMOTION,
    WOUND,
    LOYALTY,
    DISAGREEMENT,
}

@Serializable
data class CommanderDevelopmentEvent(
    val kind: CommanderEventKind,
    val commanderId: Long,
    val otherCommanderId: Long? = null,
    val day: Int,
)

/** Initial event content for a later court-event scheduler; none is silently applied on migration. */
data class CommanderEventDefinition(val kind: CommanderEventKind, val title: String, val text: String)
val commanderEventCatalog = listOf(
    CommanderEventDefinition(CommanderEventKind.RIVALRY,"Rivalen im Kriegsrat","Zwei Kommandanten konkurrieren um den nächsten Einsatz."),
    CommanderEventDefinition(CommanderEventKind.FRIENDSHIP,"Waffenbrüder","Eine gemeinsam gehaltene Linie stärkt den Zusammenhalt."),
    CommanderEventDefinition(CommanderEventKind.PROMOTION,"Neue Verantwortung","Die Siege rechtfertigen eine höhere Stellung."),
    CommanderEventDefinition(CommanderEventKind.WOUND,"Verwundete Führungskraft","Die Bergung eines Kommandanten steht vor der nächsten Offensive."),
    CommanderEventDefinition(CommanderEventKind.LOYALTY,"Treue im Zweifel","Der Hof würdigt den Einsatz einer loyalen Führungskraft."),
    CommanderEventDefinition(CommanderEventKind.DISAGREEMENT,"Uneiniger Kriegsrat","Versorgung und Angriff verlangen eine gemeinsame Entscheidung."),
)
