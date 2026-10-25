@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/*
 * v1.1 "Krone": the strategic layer above the home fortress.
 * Kronlande (provinces), Heerbann and mercenaries, trade partners, edicts,
 * heroines (meet -> recruit as field marshal -> companion -> queen),
 * personal combat arts with duels and a hidden developer menu.
 * Every field is additive with safe defaults so older saves load unchanged.
 */

/** Chronicle entries kept in the save; older lines fade out so long campaigns stay small and fast. */
const val CHRONICLE_LIMIT = 250

@Serializable
enum class CrownLand(val label: String, val description: String) {
    GRENZMARK("Grenzmark", "Umkämpftes Grenzland vor deiner Feste. Räuberbarone und freie Dörfer."),
    ALDMARK("Aldmark", "Reiches Menschenland mit Ritterburgen, Turnieren und alten Häusern."),
    WALDHAIN("Waldhain", "Uralte Wälder der Waldelben, Jagdgründe und versteckte Dörfer."),
    SONNENHAIN("Sonnenhain", "Goldene Elbenstädte voller Kunst, Magie und stolzer Garden."),
    KUESTENREICH("Küstenreich", "Häfen, Handelsstädte und Fischerdörfer am Silbermeer."),
    KRANICHWALL("Kranichwall", "Festungsstädte entlang des großen Walls, Heimat der Mauerlegion."),
    ASCHESTEPPE("Hordenland", "Verbrannte Ebenen der Orks und Uruk-hai. Aus den Kratern schwärmen die Tao Tei."),
}

@Serializable
enum class ProvinceOwner(val label: String) {
    PLAYER("Dein Reich"),
    FREE("Freies Land"),
    BANDITS("Räuberbarone"),
    RIVAL("Fremde Krone"),
    HORDE("Horden"),
    REBELS("Aufständische"),
}

/** Persisted province state. Static facts (name, land, terrain, people, wealth) come from [CrownCatalog]. */
@Serializable
data class CrownProvince(
    val id: String,
    val owner: ProvinceOwner,
    val population: Int,
    val garrison: Int,
    val baseGarrison: Int,
    /** Fighting value per defender, roughly comparable to unit attack+defense+ranged. */
    val garrisonQuality: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val development: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val fortification: Int = 0,
    val loyalty: Int = 50,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val unrest: Int = 0,
    /** Diplomatic favor toward the player, used to annex foreign provinces without war. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val favor: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val acquiredDay: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val lastLevyDay: Int = -999,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val lastFeastDay: Int = -999,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val lastEnvoyDay: Int = -999,
) {
    private val info: CrownCatalog.ProvinceInfo get() = CrownCatalog.info(id)
    val name: String get() = info.name
    val land: CrownLand get() = info.land
    val terrain: String get() = info.terrain
    val culture: Culture get() = info.culture
    /** 1..10, drives gold and loot. */
    val wealth: Int get() = info.wealth
    /** Monster kind holding a horde province; captured provinces fall back to orcs. */
    val horde: EnemyType get() = info.horde ?: EnemyType.ORC
    /** Readable owner: "Orks", "Uruk-hai" or "Tao Tei" instead of a generic horde label. */
    val ownerLabel: String get() = if (owner == ProvinceOwner.HORDE) CrownCatalog.hordeName(horde) else owner.label
}

@Serializable
enum class CrownEdict(val label: String, val description: String) {
    CONSCRIPTION("Allgemeine Wehrpflicht", "Kronlande stellen 2,5× so viele Rekruten, Ausbildung 15 % billiger. Die Stimmung sinkt langsam."),
    WAR_ECONOMY("Kriegswirtschaft", "Ausbildung halb so lang und 40 % billiger, Provinzen schmieden 4× so viele Waffen. −20 % Provinzgold."),
    FREE_TRADE("Freihandel", "Handelsrouten bringen 35 % mehr Gewinn und Beziehungen wachsen schneller."),
    SETTLEMENT("Landnahme", "Provinzbevölkerung wächst doppelt so schnell, Landkauf ist 30 % günstiger."),
    IRON_FIST("Eiserne Hand", "Unruhen sinken jeden Tag deutlich. Eroberte Provinzen werden schneller loyal, aber Zufriedenheit leidet."),
    ROYAL_PATRONAGE("Königliche Gunst", "Feste und Provinzausbau kosten weniger und heben Loyalität stärker."),
}

@Serializable
data class MercenaryContract(
    val id: String,
    val companyId: String,
    val type: UnitType,
    val soldiers: Int,
    val upkeep: Int,
    val hiredDay: Int,
)

@Serializable
data class TradePartnerState(
    val id: String,
    val relation: Int = 15,
    val unlocked: Boolean = false,
    /** 0 = none, 1 = Handelsabkommen, 2 = Bündnis. */
    val agreement: Int = 0,
    val routes: Int = 0,
    val lastGiftDay: Int = -999,
    val totalProfit: Long = 0,
    val lastIncome: Int = 0,
)

/** A hostile army marching against one of your crown provinces. */
@Serializable
data class CrownThreat(
    val id: String,
    val targetId: String,
    val sourceId: String,
    val attacker: ProvinceOwner,
    val soldiers: Int,
    val quality: Int,
    val announcedDay: Int,
    val arrivalDay: Int,
    /** Readable attacker name, e.g. "Uruk-hai" or "Fremde Krone". */
    val attackerName: String = "",
) {
    val label: String get() = attackerName.ifBlank { attacker.label }
}

/** A personal wish of the queen with a deadline, e.g. freeing her homeland. */
@Serializable
data class RoyalGoal(
    val title: String,
    val provinceId: String,
    val deadlineDay: Int,
    val rewardGold: Int,
    val rewardRenown: Int,
)

@Serializable
data class CampaignReport(
    val day: Int,
    val provinceId: String,
    val provinceName: String,
    val victory: Boolean,
    val ownStart: Int,
    val ownLosses: Int,
    val enemyStart: Int,
    val enemyLosses: Int,
    val loot: Int,
    val renown: Int,
    val text: String,
)

@Serializable
enum class CombatArt(val label: String, val description: String, val technique: String) {
    BLADE("Klingentanz", "Schwertkunst für Angriff und Duelle.", "Wirbelhieb"),
    SHIELD("Schildwall", "Paraden, Konter und Standfestigkeit.", "Eiserner Konter"),
    BOW("Adlerauge", "Präzise Schüsse auf Distanz.", "Doppelschuss"),
    RIDING("Sturmritt", "Berittener Kampf und Lanzenangriffe.", "Lanzenstoß"),
    WARCRY("Kriegsruf", "Deine Präsenz treibt Heere vorwärts.", "Schlachtruf"),
    STRATEGY("Feldherrnblick", "Absichten lesen, Schwächen erkennen.", "Vorahnung"),
}

@Serializable
enum class DuelStance(val label: String, val hint: String) {
    ATTACK("Angriff", "Schlägt Finten, scheitert an Paraden."),
    DEFEND("Parade", "Kontert Angriffe, fällt auf Finten herein."),
    FEINT("Finte", "Überlistet Paraden, wird von Angriffen getroffen."),
    TECHNIQUE("Technik", "Starke Spezialtechnik. Kostet 3 Fokus."),
}

@Serializable
data class DuelSession(
    val context: String,
    val opponentName: String,
    val opponentTitle: String,
    val opponentSkill: Int,
    val playerHp: Int = 100,
    val opponentHp: Int = 100,
    val focus: Int = 2,
    val round: Int = 1,
    val intentHint: String = "",
    val intendedStance: DuelStance = DuelStance.ATTACK,
    val log: List<String> = emptyList(),
    val finished: Boolean = false,
    val won: Boolean = false,
)

@Serializable
data class CombatArtsState(
    val levels: Map<CombatArt, Int> = emptyMap(),
    val experience: Map<CombatArt, Int> = emptyMap(),
    val lastTrainingDay: Int = -1,
    val duelsWon: Int = 0,
    val duelsLost: Int = 0,
    val tournamentWins: Int = 0,
    val lastTournamentDay: Int = -999,
) {
    fun level(art: CombatArt): Int = levels[art] ?: 0
    fun xp(art: CombatArt): Int = experience[art] ?: 0
}

@Serializable
enum class HeroineStage(val label: String) {
    RUMOR("Gerücht"),
    TRAVELING("Unterwegs"),
    MET("Begegnet"),
    TRIAL("Bewährungsprobe"),
    COMPANION("Deine Feldherrin"),
    GENERAL("Feldherrin eines Heeres"),
    DEPARTED("Fortgezogen"),
}

@Serializable
data class HeroineState(
    val id: String,
    val stage: HeroineStage = HeroineStage.RUMOR,
    val arrivalDay: Int? = null,
    val metDay: Int? = null,
    /** Her opinion of you before she serves you (0..100). */
    val favor: Int = 0,
    val trialStartDay: Int? = null,
    val trialStartVictories: Int = 0,
    val trialDuelWon: Boolean = false,
    val recruitedDay: Int? = null,
    val firstImpression: String = "",
)

@Serializable
data class SagaOption(val id: String, val label: String, val hint: String = "")

@Serializable
data class SagaScene(
    val key: String,
    val heroineId: String,
    val title: String,
    val location: String,
    val text: String,
    val options: List<SagaOption>,
    /** Optional speaker for non-heroine scenes (kings, bosses, campaign events). */
    val speaker: String? = null,
    val portraitKey: String? = null,
    val look: PortraitSpec? = null,
)

/** The future partner the player designs at character creation. She is met after about a week. */
@Serializable
data class PartnerProfile(
    val name: String,
    val age: Int,
    val species: Species,
    /** Heroine template whose path, place and story she follows: alina, seraphine, liang or ithiliel. */
    val background: String,
    val portraitUri: String? = null,
    val weapon: String? = null,
    val armorStyle: String? = null,
    val look: PortraitSpec? = null,
)

@Serializable
data class HeroineSagaState(
    /** False for campaigns whose companion was met by the old fixed day-5 arrival. */
    val enabled: Boolean = false,
    val heroines: List<HeroineState> = emptyList(),
    /** Heroine serving as the persistent companion; "legacy" for a companion from older saves. */
    val companionId: String? = null,
    val chapter: Int = 0,
    val chapterStartDay: Int = 0,
    val chapterStartVictories: Int = 0,
    val pendingScene: SagaScene? = null,
    val lastOutcome: String? = null,
    val lastRoyalDay: Int = 0,
    val royalCount: Int = 0,
    val goal: RoyalGoal? = null,
    /** Custom partner from character creation; replaces the heroine of the same background. */
    val partner: PartnerProfile? = null,
    /** The companion is wounded and cannot fight until this day. */
    val companionWoundedUntil: Int = 0,
) {
    val partnerId: String? get() = partner?.background
}

@Serializable
data class CrownState(
    val initialized: Boolean = false,
    /** Only provinces that differ from their catalog start are persisted; see [provinces]. */
    val provinceChanges: List<CrownProvince> = emptyList(),
    val edicts: List<CrownEdict> = emptyList(),
    val mercenaries: List<MercenaryContract> = emptyList(),
    val partners: List<TradePartnerState> = emptyList(),
    val saga: HeroineSagaState = HeroineSagaState(),
    val arts: CombatArtsState = CombatArtsState(),
    val duel: DuelSession? = null,
    val reports: List<CampaignReport> = emptyList(),
    val lastCampaignDay: Int = -1,
    val campaignsToday: Int = 0,
    val campaignVictories: Int = 0,
    val lastLevyDay: Int = -999,
    val championBonusProvinceId: String? = null,
    val championPenaltyProvinceId: String? = null,
    val lastTradeIncome: Int = 0,
    val lastMercenaryUpkeep: Int = 0,
    val lastSettlers: Int = 0,
    val threats: List<CrownThreat> = emptyList(),
    val lastThreatDay: Int = 0,
    val liveCampaignProvinceId: String? = null,
    val act: ActState = ActState(),
    val rivals: List<RivalState> = emptyList(),
    val celebrations: List<Celebration> = emptyList(),
    val pendingMarch: PendingMarch? = null,
    val playerLook: PortraitSpec? = null,
    val devMode: Boolean = false,
    val devCheatsUsed: Int = 0,
    val godMode: Boolean = false,
    val nextId: Long = 1,
) {
    /** All 42 provinces: catalog start state overlaid with persisted changes. */
    val provinces: List<CrownProvince>
        get() {
            if (provinceChanges.isEmpty()) return CrownCatalog.startProvinces
            val changed = provinceChanges.associateBy { it.id }
            return CrownCatalog.startProvinces.map { changed[it.id] ?: it }
        }

    fun withProvinces(list: List<CrownProvince>): CrownState =
        copy(provinceChanges = list.filter { CrownCatalog.startProvince(it.id) != it })

    val ownedProvinces: List<CrownProvince>
        get() = provinceChanges.filter { it.owner == ProvinceOwner.PLAYER }

    val subjects: Long
        get() = ownedProvinces.sumOf { it.population.toLong() }

    fun partner(id: String): TradePartnerState? = partners.firstOrNull { it.id == id }

    fun heroine(id: String): HeroineState? = saga.heroines.firstOrNull { it.id == id }
}
