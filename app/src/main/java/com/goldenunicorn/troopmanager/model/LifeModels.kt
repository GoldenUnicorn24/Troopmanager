package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

/*
 * Project Rebirth (save schema 6): the personal RPG layer.
 *
 * A [LifeState] describes the player as a person living in the same world as the strategic
 * layer: how the game was started, where the character is right now, what time of day it is,
 * how much money is in the purse and which people (persistent [NpcIdentity]s) live in the world.
 * Every field is additive with defaults that reproduce the classic border-lord game, so schema-5
 * saves load unchanged as [GameMode.BORDER_LORD].
 */

@Serializable
enum class GameMode(val label: String, val tagline: String, val description: String, val landed: Boolean) {
    SANDBOX(
        "Sandbox", "Offene Welt, keine Pflichtquest",
        "Wähle jede Herkunft – vom Bauernsohn bis zum Adelserben. Die Welt lebt, Königreiche führen Krieg, aber niemand schreibt dir vor, was du tust.",
        landed = false,
    ),
    STORY(
        "Geschichte", "Die drei Akte mit Bosskämpfen",
        "Der klassische Start als Grenzherr mit Festung und Armee. Die Kampagnenakte, Rivalenkönige und Bosse führen erzählerisch durch das Spiel.",
        landed = true,
    ),
    BORDER_LORD(
        "Grenzherr", "Festung, Armee, Reich",
        "Der vertraute Start mit Grenzfeste, Gebiet und stehender Armee. Die Kampagnenakte kannst du zu- oder abschalten.",
        landed = true,
    ),
    FREE_LIFE(
        "Freies Leben", "Ohne Titel, Land oder Soldaten",
        "Du beginnst als gewöhnlicher Mensch mit wenig Geld. Arbeit, Begegnungen, Tavernen und Straßen – wohin dein Leben führt, entscheidest du.",
        landed = false,
    ),
}

@Serializable
enum class Gender(val label: String) { MALE("Mann"), FEMALE("Frau"), DIVERSE("Divers") }

/** Coarse time of day for RPG places. The strategic day counter stays [GameState.day]. */
@Serializable
enum class TimeSlot(val label: String, val clock: String) {
    MORNING("Morgen", "07:00"),
    NOON("Mittag", "12:00"),
    AFTERNOON("Nachmittag", "15:00"),
    EVENING("Abend", "19:00"),
    NIGHT("Nacht", "23:00");

    val next: TimeSlot? get() = entries.getOrNull(ordinal + 1)
}

@Serializable
enum class WealthLevel(val label: String, val gold: Int) {
    POOR("Mittellos", 25), MODEST("Bescheiden", 90), COMFORTABLE("Wohlhabend", 320), RICH("Reich", 1200),
}

/**
 * Social origin. Attribute hints reuse the six existing attributes (sword, bow, riding,
 * leadership, tactics, diplomacy); there is no second attribute system.
 */
@Serializable
enum class Background(
    val label: String,
    val profession: String,
    val description: String,
    val wealth: WealthLevel,
    val renown: Int,
    val freeLife: Boolean,
    val landed: Boolean = false,
    val hint: String = "",
) {
    VILLAGER("Dorfbewohner", "Bauer", "Aufgewachsen zwischen Feldern und Viehweiden. Zäh, unscheinbar, von allen unterschätzt.", WealthLevel.POOR, 0, true, hint = "Reiten, Diplomatie"),
    RECRUIT("Rekrut", "Rekrut", "Frisch eingeschrieben, mit Speer, Helm und großen Träumen.", WealthLevel.POOR, 4, true, hint = "Schwert, Führung"),
    MERCENARY("Söldner", "Söldner", "Du kämpfst für den, der zahlt. Narben, Erfahrung und ein leerer Beutel.", WealthLevel.MODEST, 10, true, hint = "Schwert, Taktik"),
    ADVENTURER("Abenteurer", "Abenteurer", "Ruinen, Karten, Gerüchte: Du folgst jeder Spur, die nach Gold oder Geschichte riecht.", WealthLevel.MODEST, 6, true, hint = "Bogen, Reiten"),
    MERCHANT("Händler", "Händler", "Ein Karren, ein paar Kontakte und ein Gespür für Preise.", WealthLevel.COMFORTABLE, 4, true, hint = "Diplomatie, Taktik"),
    TOWN_GUARD("Stadtwache", "Stadtwache", "Du kennst jede Gasse, jeden Trunkenbold und jeden Schmuggler deiner Stadt.", WealthLevel.MODEST, 8, true, hint = "Schwert, Führung"),
    HUNTER("Jäger", "Jäger", "Wald, Wild und Spuren sind dein Zuhause. Menschen sind dir oft fremder als Wölfe.", WealthLevel.POOR, 3, true, hint = "Bogen, Reiten"),
    POOR_NOBLE("Verarmter Adliger", "Landloser Edler", "Ein alter Name, ein verpfändetes Gut und Gläubiger vor der Tür.", WealthLevel.MODEST, 18, false, hint = "Diplomatie, Führung"),
    NOBLE_HEIR("Erbe eines Hauses", "Adelserbe", "Spross eines einflussreichen Hauses – Geld, Beziehungen und hohe Erwartungen.", WealthLevel.RICH, 30, false, hint = "Diplomatie, Reiten"),
    BORDER_LORD("Grenzherr", "Grenzherr", "Herr über eine Grenzfeste mit eigenem Gebiet und stehender Armee.", WealthLevel.RICH, 0, false, landed = true, hint = "Führung, Taktik"),
}

/** Personal rank on the way from commoner to crown; unlocked by renown and deeds, not forced. */
@Serializable
enum class CareerRank(val label: String, val renown: Int) {
    COMMONER("Gewöhnlicher Bürger", 0),
    RECRUIT("Rekrut", 0),
    SOLDIER("Erfahrener Soldat", 25),
    MERCENARY("Söldner", 25),
    CAPTAIN("Hauptmann", 70),
    KNIGHT("Ritter", 100),
    COMMANDER("Kommandant", 130),
    LORD("Burgherr", 200),
}

/** Places inside a town that can be visited in person. Only the tavern is walkable in this milestone. */
@Serializable
enum class LifePlace(val label: String, val walkable: Boolean) {
    TOWN("Stadtplatz", false),
    TAVERN("Taverne", true),
    MARKET("Markt", true),
    BARRACKS("Kaserne", true),
    CASTLE("Burghof", true),
    AWAY("Unterwegs", false),
}

/** A town with a tavern. Province ids refer to the crown layer, so both views share one world. */
data class TownDef(val provinceId: String, val tavernName: String, val innkeeperName: String, val flavor: String)

object Towns {
    val all: List<TownDef> = listOf(
        TownDef("gm_rabenfurt", "Zum Rostigen Kessel", "Hagen Bräunling", "Ein Grenzstädtchen an der Furt, Schilf, Nebel und Flößer."),
        TownDef("gm_eschenhof", "Die Fette Gans", "Wilma Eschner", "Weidedorf mit Viehmarkt und lauten Hirten."),
        TownDef("al_aldenburg", "Zum Goldenen Turnierhelm", "Bertram Haller", "Turnierstadt voller Ritter, Knappen und Wettbüros."),
        TownDef("ks_saltmere", "Der Krumme Anker", "Maris Tangsalz", "Freihafen am Silbermeer, Seeleute aus aller Welt."),
        TownDef("kw_jadehain", "Haus der Neun Teeblüten", "Wen Lao", "Teegärten am großen Wall, Händler der Mauerlegion."),
        TownDef("wh_mondquell", "Quellhalle", "Elarin Mondfluss", "Waldelbische Quellstadt, Laternen in den Bäumen."),
        TownDef("as_freistatt", "Zur Letzten Zuflucht", "Grit Aschenbein", "Lagerstadt Entflohener am Rand des Hordenlands."),
    )

    fun byProvince(id: String): TownDef? = all.firstOrNull { it.provinceId == id }
    fun startable(): List<TownDef> = all.filter { it.provinceId != "as_freistatt" }
}

/**
 * Detailed, persistent look of a person. Small integer indices keep saves tiny; sliders are 0..100.
 * The same record drives the editor, the tavern figure, dialogue portraits and legacy screens
 * (via [toPortraitSpec]), so a person stays recognisably the same everywhere.
 */
@Serializable
data class CharacterAppearance(
    val faceShape: Int = 0,
    val jaw: Int = 50,
    val cheekbones: Int = 50,
    val noseSize: Int = 50,
    val noseShape: Int = 0,
    val eyeShape: Int = 0,
    val eyeColor: Int = 0,
    val browStyle: Int = 0,
    val lips: Int = 50,
    val mouthWidth: Int = 50,
    val ears: Int = 0,
    val skinTone: Int = 1,
    val scar: Int = 0,
    val tattoo: Int = 0,
    val beard: Int = 0,
    val hairStyle: Int = 0,
    val hairLength: Int = 40,
    val hairColor: Int = 1,
    val hairTexture: Int = 0,
    val height: Int = 50,
    val build: Int = 1,
    val muscle: Int = 50,
    val posture: Int = 0,
    val clothing: Int = 0,
    val armor: Int = 1,
    val cloak: Int = 1,
    val jewelry: Int = 0,
    val weapon: Int = 0,
    val primaryColor: Int = 0,
    val accentColor: Int = 2,
    val expression: Int = 0,
    val background: Int = 0,
) {
    fun normalized(): CharacterAppearance = copy(
        faceShape = faceShape.coerceIn(0, AppearanceOptions.faceShapes.lastIndex),
        jaw = jaw.coerceIn(0, 100), cheekbones = cheekbones.coerceIn(0, 100), noseSize = noseSize.coerceIn(0, 100),
        noseShape = noseShape.coerceIn(0, AppearanceOptions.noseShapes.lastIndex),
        eyeShape = eyeShape.coerceIn(0, AppearanceOptions.eyeShapes.lastIndex),
        eyeColor = eyeColor.coerceIn(0, AppearanceOptions.eyeColors.lastIndex),
        browStyle = browStyle.coerceIn(0, AppearanceOptions.browStyles.lastIndex),
        lips = lips.coerceIn(0, 100), mouthWidth = mouthWidth.coerceIn(0, 100),
        ears = ears.coerceIn(0, AppearanceOptions.ears.lastIndex),
        skinTone = skinTone.coerceIn(0, AppearanceOptions.skinTones.lastIndex),
        scar = scar.coerceIn(0, AppearanceOptions.scars.lastIndex),
        tattoo = tattoo.coerceIn(0, AppearanceOptions.tattoos.lastIndex),
        beard = beard.coerceIn(0, AppearanceOptions.beards.lastIndex),
        hairStyle = hairStyle.coerceIn(0, AppearanceOptions.hairStyles.lastIndex),
        hairLength = hairLength.coerceIn(0, 100),
        hairColor = hairColor.coerceIn(0, AppearanceOptions.hairColors.lastIndex),
        hairTexture = hairTexture.coerceIn(0, AppearanceOptions.hairTextures.lastIndex),
        height = height.coerceIn(0, 100), build = build.coerceIn(0, AppearanceOptions.builds.lastIndex),
        muscle = muscle.coerceIn(0, 100), posture = posture.coerceIn(0, AppearanceOptions.postures.lastIndex),
        clothing = clothing.coerceIn(0, AppearanceOptions.clothing.lastIndex),
        armor = armor.coerceIn(0, AppearanceOptions.armor.lastIndex),
        cloak = cloak.coerceIn(0, AppearanceOptions.cloaks.lastIndex),
        jewelry = jewelry.coerceIn(0, AppearanceOptions.jewelry.lastIndex),
        weapon = weapon.coerceIn(0, AppearanceOptions.weapons.lastIndex),
        primaryColor = primaryColor.coerceIn(0, AppearanceOptions.fabricColors.lastIndex),
        accentColor = accentColor.coerceIn(0, AppearanceOptions.fabricColors.lastIndex),
        expression = expression.coerceIn(0, AppearanceOptions.expressions.lastIndex),
        background = background.coerceIn(0, AppearanceOptions.backgrounds.lastIndex),
    )

    /** Mapping onto the v1.3 drawn-portrait recipe for every screen that still uses it. */
    fun toPortraitSpec(): PortraitSpec = PortraitSpec(
        skin = skinTone.coerceIn(0, 5),
        hair = when (hairStyle) { 0, 1 -> 0; 2 -> 1; 3, 5 -> 2; 4 -> 3; 6 -> 4; else -> 5 },
        hairColor = hairColor.coerceIn(0, 7),
        eyes = eyeColor.coerceIn(0, 5),
        elfEars = ears > 0,
        beard = when (beard) { 0 -> 0; 1 -> 1; 2 -> 3; else -> 2 },
        armor = when (armor) { 4 -> 0; 1 -> 1; 0 -> 2; else -> 3 },
        armorColor = primaryColor.coerceIn(0, 5),
        marking = when { tattoo == 2 -> 3; tattoo > 0 -> 2; scar > 0 -> 1; else -> 0 },
        background = background.coerceIn(0, 5),
    )
}

/** Labels for every appearance option. Palettes with colors live in the UI. */
object AppearanceOptions {
    val faceShapes = listOf("Oval", "Rund", "Kantig", "Herzförmig", "Schmal")
    val noseShapes = listOf("Gerade", "Adlernase", "Stupsnase", "Breit")
    val eyeShapes = listOf("Mandel", "Rund", "Schmal", "Schräg")
    val eyeColors = listOf("Braun", "Dunkel", "Grün", "Blau", "Bernstein", "Grau")
    val browStyles = listOf("Gerade", "Geschwungen", "Buschig", "Fein")
    val ears = listOf("Rund", "Spitz", "Lang & spitz")
    val skinTones = listOf("Hell", "Warm", "Bronze", "Oliv", "Dunkel", "Tief")
    val scars = listOf("Keine", "Wange", "Über dem Auge", "Stirn", "Kinn")
    val tattoos = listOf("Keine", "Stammeslinien", "Elbenranke", "Mauerzeichen")
    val beards = listOf("Keiner", "Stoppeln", "Kinnbart", "Vollbart", "Langer Bart")
    val hairStyles = listOf("Kurz", "Gescheitelt", "Lang offen", "Zopf", "Dutt", "Geflochten", "Wild", "Kahl")
    val hairColors = listOf("Schwarz", "Dunkelbraun", "Braun", "Waldgrün", "Kupferrot", "Platin", "Grau", "Goldblond")
    val hairTextures = listOf("Glatt", "Wellig", "Lockig")
    val builds = listOf("Schlank", "Athletisch", "Kräftig", "Stämmig")
    val postures = listOf("Aufrecht", "Locker", "Stolz", "Vorsichtig")
    val clothing = listOf("Leinentunika", "Lederwams", "Reisekleid", "Gambeson", "Edle Robe")
    val armor = listOf("Keine", "Leder", "Kettenhemd", "Schuppen", "Platte")
    val cloaks = listOf("Kein Umhang", "Wollumhang", "Pelzumhang", "Elbenmantel")
    val jewelry = listOf("Keiner", "Ring", "Halskette", "Ohrringe", "Diadem")
    val weapons = listOf("Langschwert", "Bogen", "Speer", "Streitaxt", "Zwillingsdolche", "Stab")
    val fabricColors = listOf("Mitternacht", "Waldgrün", "Gold", "Weinrot", "Stahlgrau", "Schwarz", "Erdbraun", "Elfenbein")
    val expressions = listOf("Ruhig", "Lächelnd", "Ernst", "Entschlossen")
    val backgrounds = listOf("Nacht", "Wald", "Sonne", "Purpur", "See", "Glut")
}

/** Personality on 0..100 axes. Drives dialogue outcomes, routines and consent decisions. */
@Serializable
data class NpcPersonality(
    val warmth: Int = 50,
    val boldness: Int = 50,
    val honor: Int = 50,
    val humor: Int = 50,
    val curiosity: Int = 50,
    val temper: Int = 30,
) {
    fun normalized() = NpcPersonality(
        warmth.coerceIn(0, 100), boldness.coerceIn(0, 100), honor.coerceIn(0, 100),
        humor.coerceIn(0, 100), curiosity.coerceIn(0, 100), temper.coerceIn(0, 100),
    )

    /** Short readable traits, strongest first. */
    fun traits(): List<String> {
        val t = mutableListOf<Pair<Int, String>>()
        if (warmth >= 65) t += warmth to "herzlich" else if (warmth <= 30) t += (100 - warmth) to "kühl"
        if (boldness >= 65) t += boldness to "wagemutig" else if (boldness <= 30) t += (100 - boldness) to "vorsichtig"
        if (honor >= 65) t += honor to "ehrenhaft" else if (honor <= 30) t += (100 - honor) to "gerissen"
        if (humor >= 65) t += humor to "humorvoll" else if (humor <= 30) t += (100 - humor) to "ernst"
        if (curiosity >= 65) t += curiosity to "neugierig"
        if (temper >= 65) t += temper to "hitzköpfig"
        return t.sortedByDescending { it.first }.map { it.second }.ifEmpty { listOf("ausgeglichen") }
    }
}

@Serializable
enum class NpcRole(val label: String) {
    INNKEEPER("Wirt"),
    REGULAR("Stammgast"),
    TRAVELER("Reisende"),
    MERCENARY_CAPTAIN("Söldnerführer"),
    MERCHANT("Händler"),
    NOBLE("Adlige"),
    BARD("Spielmann"),
    COMPANION("Mögliche Gefährtin"),
    SELLSWORD("Söldner"),
    SMITH("Schmied"),
    HEALER("Heilerin"),
    SERGEANT("Ausbilder"),
    LORD("Burgvogt"),
}

/** Social stage between the player and one person. Romance stages are never reached automatically. */
@Serializable
enum class SocialStage(val label: String, val romantic: Boolean = false) {
    STRANGER("Fremd"),
    ACQUAINTANCE("Bekannt"),
    FRIEND("Befreundet"),
    CLOSE_FRIEND("Enge Freundschaft"),
    FLIRT("Flirt", true),
    LOVER("Affäre", true),
    PARTNER("Feste Beziehung", true),
    ENGAGED("Verlobt", true),
    MARRIED("Verheiratet", true),
}

@Serializable
data class NpcRelation(
    val stage: SocialStage = SocialStage.STRANGER,
    /** -100..100: how much this person likes the player. */
    val affinity: Int = 0,
    val trust: Int = 0,
    val attraction: Int = 0,
    val rivalry: Int = 0,
    val timesMet: Int = 0,
    val lastTalkDay: Int = -1,
    val lastTalkSlot: TimeSlot? = null,
    /** Topics already used in the current slot, so the same compliment cannot be spammed. */
    val topicsThisSlot: List<String> = emptyList(),
    val declinedRomance: Int = 0,
    /** The person asked not to be courted; romantic options stay closed until trust is rebuilt. */
    val romanceBoundary: Boolean = false,
    val intimateNights: Int = 0,
    val lastIntimateDay: Int = -100,
    /** Day the person learned of the player's affair with someone else. */
    val hurtByAffairDay: Int? = null,
) {
    fun normalized() = copy(
        affinity = affinity.coerceIn(-100, 100), trust = trust.coerceIn(0, 100),
        attraction = attraction.coerceIn(0, 100), rivalry = rivalry.coerceIn(0, 100),
        timesMet = timesMet.coerceAtLeast(0), declinedRomance = declinedRomance.coerceAtLeast(0),
        intimateNights = intimateNights.coerceAtLeast(0), topicsThisSlot = topicsThisSlot.takeLast(12),
    )
}

@Serializable
data class NpcMemory(
    val key: String,
    val day: Int,
    val text: String,
    /** Positive = good memory, negative = grudge. */
    val weight: Int = 1,
)

/** Where a person usually is at a given time of day; [LifePlace.AWAY] means elsewhere/at home. */
@Serializable
data class NpcRoutine(
    val morning: LifePlace = LifePlace.MARKET,
    val noon: LifePlace = LifePlace.TOWN,
    val afternoon: LifePlace = LifePlace.MARKET,
    val evening: LifePlace = LifePlace.TAVERN,
    val night: LifePlace = LifePlace.AWAY,
    /** 0..100 chance-like regularity: high values almost always keep the routine. */
    val regularity: Int = 70,
) {
    fun at(slot: TimeSlot): LifePlace = when (slot) {
        TimeSlot.MORNING -> morning
        TimeSlot.NOON -> noon
        TimeSlot.AFTERNOON -> afternoon
        TimeSlot.EVENING -> evening
        TimeSlot.NIGHT -> night
    }
}

/** A persistent person. Name, look and personality never change on their own. */
@Serializable
data class NpcIdentity(
    val id: String,
    val name: String,
    val gender: Gender,
    val age: Int,
    val species: Species,
    val culture: Culture,
    val origin: String,
    val profession: String,
    val socialStanding: String,
    val backstory: String,
    val appearance: CharacterAppearance,
    val personality: NpcPersonality,
    val interests: List<String> = emptyList(),
    val likes: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
    val goal: String = "",
    val morals: String = "",
    val combatStyle: String = "",
    val role: NpcRole = NpcRole.REGULAR,
    val homeProvinceId: String,
    val routine: NpcRoutine = NpcRoutine(),
    /** Whether this person is open to romance at all, and towards whom. */
    val romanceable: Boolean = false,
    val attractedTo: Set<Gender> = emptySet(),
    /** Created by the player at the start as a possible future companion. */
    val designedByPlayer: Boolean = false,
    val relation: NpcRelation = NpcRelation(),
    val memories: List<NpcMemory> = emptyList(),
    val intoxication: Int = 0,
    val metDay: Int? = null,
    /** The person has left the region for good (e.g. moved away); kept for memories and saves. */
    val departed: Boolean = false,
) {
    val firstName: String get() = name.substringBefore(' ')
    fun remembers(key: String): Boolean = memories.any { it.key == key }
}

/** A small job offered in a tavern. Accepting, finishing or failing it changes the saved state. */
@Serializable
data class LifeJob(
    val id: String,
    val title: String,
    val description: String,
    val giverId: String,
    val provinceId: String,
    val skill: String,
    val difficulty: Int,
    val reward: Int,
    val renown: Int,
    val slots: Int,
    val offeredDay: Int,
    val accepted: Boolean = false,
    val done: Boolean = false,
    val failed: Boolean = false,
    /** Fought out personally in a skirmish instead of a single skill check. */
    val combat: Boolean = false,
    val enemy: String = "",
)

/** Something that happened in the world, heard as a rumor in taverns. */
@Serializable
data class WorldNews(val day: Int, val provinceId: String, val title: String, val text: String, val severity: Int = 1)

/** How the optional future companion was prepared at character creation. */
@Serializable
enum class CompanionChoice(val label: String) {
    FULL("Vollständig erstellt"),
    PARTIAL("Teilweise erstellt"),
    NONE("Nicht erstellt"),
}

@Serializable
data class LifeState(
    val mode: GameMode = GameMode.BORDER_LORD,
    /** False while the character owns no realm; the realm fields then lie dormant and unsimulated. */
    val landed: Boolean = true,
    /** Whether the three campaign acts with bosses drive the story. */
    val storyActs: Boolean = true,
    val background: Background = Background.BORDER_LORD,
    val profession: String = "Grenzherr",
    val gender: Gender = Gender.MALE,
    val culture: Culture = Culture.HUMAN,
    val origin: String = "",
    val backstory: String = "",
    val homeProvinceId: String = "gm_rabenfurt",
    val appearance: CharacterAppearance? = null,
    val purse: Int = 0,
    val renown: Int = 0,
    val career: CareerRank = CareerRank.COMMONER,
    val provinceId: String = "gm_rabenfurt",
    val place: LifePlace = LifePlace.TOWN,
    val timeSlot: TimeSlot = TimeSlot.MORNING,
    val intoxication: Int = 0,
    val hangover: Boolean = false,
    val fatigue: Int = 0,
    val fed: Boolean = true,
    val roomPaidDay: Int? = null,
    val travel: LifeTravel? = null,
    val npcs: List<NpcIdentity> = emptyList(),
    val companionChoice: CompanionChoice = CompanionChoice.NONE,
    val companionNpcId: String? = null,
    val jobs: List<LifeJob> = emptyList(),
    val news: List<WorldNews> = emptyList(),
    val lastPulseDay: Int = 0,
    val diceWins: Int = 0,
    val diceLosses: Int = 0,
    val brawls: Int = 0,
    val log: List<String> = emptyList(),
    /** Affairs a partner may still learn about: "partnerId|loverName". */
    val pendingGossip: List<String> = emptyList(),
    /** Tavern events already resolved: "day:slot:province:key". */
    val eventsDone: List<String> = emptyList(),
    // ---- Milestone 2: on the road, at the market, in the fight
    val party: List<PartyMember> = emptyList(),
    val gear: PlayerGear = PlayerGear(),
    val gifts: Map<GiftKind, Int> = emptyMap(),
    /** 0..100; reduces fighting and travel ability until healed. */
    val wound: Int = 0,
    val captiveUntil: Int? = null,
    val captor: String? = null,
    val skirmish: Skirmish? = null,
    val battles: Int = 0,
    val battleVictories: Int = 0,
    // ---- Milestone 3: people among people, the barracks
    val bonds: List<NpcBond> = emptyList(),
    val trainingDay: Int = -1,
    val trainingSessions: Int = 0,
    val enlisted: Boolean = false,
    val lastGuardDay: Int = -1,
    val lastSocialDay: Int = 0,
    // ---- Milestone 4: the castle courtyard
    val lordQuest: LordQuest? = null,
    val lordQuestsDone: Int = 0,
    val knighted: Boolean = false,
    val tournament: TournamentRun? = null,
    val tournamentsWon: Int = 0,
    val lastAudienceDay: Int = -1,
    val lastCourtDay: Int = -99,
    val lastQuestDoneDay: Int = -99,
    val nextId: Long = 1,
) {
    fun npc(id: String): NpcIdentity? = npcs.firstOrNull { it.id == id }
    val companion: NpcIdentity? get() = companionNpcId?.let(::npc)
    val partner: NpcIdentity? get() = npcs.firstOrNull { it.relation.stage in setOf(SocialStage.PARTNER, SocialStage.ENGAGED, SocialStage.MARRIED) }
    fun inParty(id: String): Boolean = party.any { it.npcId == id }
    val captive: Boolean get() = captiveUntil != null
}

/** A task from the local lord: free a province from bandits – a personal deed that changes the strategic map. */
@Serializable
data class LordQuest(val id: String, val lordId: String, val targetProvinceId: String, val enemyCount: Int, val enemyPower: Int, val reward: Int, val renown: Int, val givenDay: Int)

/** A tournament in progress: three bouts against knights of rising strength. */
@Serializable
data class TournamentRun(val provinceId: String, val day: Int, val round: Int = 0, val opponents: List<String> = emptyList(), val lost: Boolean = false)

@Serializable
enum class BondKind(val label: String) { FRIENDS("Befreundet"), RIVALS("Verfeindet"), COUPLE("Ein Paar"), SIBLINGS("Geschwister") }

/** A relationship between two non-player people. Players' deeds ripple along these lines. */
@Serializable
data class NpcBond(val a: String, val b: String, val kind: BondKind, val strength: Int = 50, val sinceDay: Int = 0) {
    fun involves(id: String) = a == id || b == id
    fun other(id: String) = if (a == id) b else a
}

/** Someone travelling and fighting with the player: a friend who joined, or a paid sellsword. */
@Serializable
data class PartyMember(val npcId: String, val joinedDay: Int, val wage: Int = 0, val wounded: Int = 0)

@Serializable
data class PlayerGear(val weaponTier: Int = 0, val armorTier: Int = 0)

data class GearItem(val tier: Int, val name: String, val price: Int, val bonus: Int, val appearanceIndex: Int, val description: String)

object GearCatalog {
    val weapons = listOf(
        GearItem(0, "Abgenutzte Klinge", 0, 0, 0, "Was du mitgebracht hast."),
        GearItem(1, "Geschmiedetes Langschwert", 25, 6, 0, "Ehrliche Arbeit eines Dorfschmieds."),
        GearItem(2, "Aldmarker Stahlschwert", 70, 12, 0, "Federnder Stahl, gute Balance."),
        GearItem(3, "Meisterklinge", 180, 20, 0, "Eine Waffe, nach der sich Söldner umdrehen."),
    )
    val armor = listOf(
        GearItem(0, "Reisekleidung", 0, 0, 0, "Kein Schutz außer Glück."),
        GearItem(1, "Lederrüstung", 20, 10, 1, "Gehärtetes Leder, leicht und leise."),
        GearItem(2, "Kettenhemd", 60, 20, 2, "Hält Klingen ab, nicht aber Keulen."),
        GearItem(3, "Schuppenpanzer", 140, 28, 3, "Schwer, aber sicher."),
        GearItem(4, "Plattenrüstung", 300, 36, 4, "Die Rüstung eines Ritters."),
    )
}

/** Small presents. A gift that matches what someone likes counts far more than an expensive one. */
@Serializable
enum class GiftKind(val label: String, val price: Int, val keywords: List<String>) {
    FLOWERS("Wiesenblumen", 2, listOf("Ruhe", "Kräuterkunde", "Bescheidenheit")),
    BOOK("Abgegriffenes Buch", 8, listOf("Bücher", "Geschichten", "Sternkunde", "Neugier")),
    WINE("Flasche Aldmarker", 6, listOf("gutes Essen", "Kochen", "Humor")),
    STRINGS("Lautensaiten", 5, listOf("Musik", "alte Lieder")),
    DAGGER("Gut gearbeiteter Dolch", 14, listOf("Mut", "Schwertkampf", "Jagd", "Abenteuer")),
    PENDANT("Silberner Anhänger", 18, listOf("Großzügigkeit", "Höflichkeit")),
}

@Serializable
enum class SkirmishKind(val label: String) { CAMPAIGN("Feldzug"), AMBUSH("Hinterhalt"), DUEL("Duell"), BEASTS("Jagd") }

@Serializable
enum class SkirmishAction(val label: String, val hint: String) {
    ATTACK("Angreifen", "Schwert und Mut – viel Schaden, aber offen für Treffer"),
    DEFEND("Verteidigen", "Halten, Wunden vermeiden, auf die Lücke warten"),
    RANGED("Bogen", "Am stärksten, solange der Feind noch nicht heran ist"),
    RALLY("Anführen", "Führung: Gefährten stärken, Feindmoral brechen"),
    RETREAT("Rückzug", "Reiten entscheidet, ob du heil davonkommst"),
}

@Serializable
enum class SkirmishOutcome(val label: String) { VICTORY("Sieg"), DEFEAT("Niederlage"), RETREAT("Rückzug"), CAPTURED("Gefangen") }

/** A personal fight, saved between rounds. Deterministic per id and round. */
@Serializable
data class Skirmish(
    val id: String,
    val kind: SkirmishKind,
    val title: String,
    val provinceId: String,
    val enemyName: String,
    val enemyCount: Int,
    val enemyPower: Int,
    val enemyHealth: Int = 100,
    val enemyMorale: Int = 100,
    val playerHealth: Int = 100,
    val allyIds: List<String> = emptyList(),
    val round: Int = 0,
    val rallied: Int = 0,
    val log: List<String> = emptyList(),
    val outcome: SkirmishOutcome? = null,
    val reward: Int = 0,
    val renown: Int = 0,
    val jobId: String? = null,
    val opponentNpcId: String? = null,
    /** A journey interrupted by an ambush continues here after the fight. */
    val travelTo: String? = null,
)

@Serializable
data class LifeTravel(val fromProvinceId: String, val toProvinceId: String, val arrivalDay: Int)

/** Drinks with price (gold), strength (intoxication) and mood. */
enum class Drink(val label: String, val price: Int, val strength: Int, val description: String) {
    WATER("Brunnenwasser", 0, 0, "Klar und kalt. Senkt den Rausch ein wenig."),
    TEA("Kräutertee", 1, 0, "Wärmt und macht den Kopf klar."),
    ALE("Dunkles Bier", 2, 12, "Das Bier des Hauses. Macht gesellig."),
    MEAD("Honigmet", 4, 18, "Süß, golden und tückisch."),
    WINE("Aldmarker Rotwein", 6, 16, "Ein Tropfen für feinere Gesellschaft."),
    SPIRIT("Feuerbrand", 5, 30, "Brennt in der Kehle. Mutig, aber gefährlich."),
}

enum class Meal(val label: String, val price: Int, val description: String) {
    STEW("Eintopf mit Brot", 3, "Sättigt und hält den Rausch im Zaum."),
    ROAST("Braten mit Wurzelgemüse", 8, "Ein festliches Mahl. Hebt die Stimmung."),
}
