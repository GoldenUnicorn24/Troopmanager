package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

@Serializable
enum class CharacterPersonality(val label: String) {
    HONORABLE("Ehrenhaft"), AMBITIOUS("Ehrgeizig"), CAUTIOUS("Bedacht"),
    COMPASSIONATE("Mitfühlend"), DISCIPLINED("Diszipliniert"), MERCANTILE("Kaufmännisch"),
}

@Serializable
enum class CommanderRank(val label: String, val capacity: Int, val victoriesRequired: Int) {
    CAPTAIN("Hauptmann", 2000, 0), GENERAL("General", 10000, 3),
    MARSHAL("Marschall", 40000, 10), SUPREME("Oberbefehlshaber", 120000, 25),
}

@Serializable
data class CareerEntry(val day: Int, val kind: String, val text: String)

@Serializable
data class CharacterDetails(
    val commanderId: Long,
    val age: Int = 30,
    val origin: String = "Grenzland",
    val family: List<String> = emptyList(),
    val personality: CharacterPersonality = CharacterPersonality.HONORABLE,
    val ambition: Int = 50,
    val courage: Int = 60,
    val diplomacy: Int = 35,
    val stewardship: Int = 35,
    val intrigue: Int = 30,
    val medicine: Int = 25,
    val rank: CommanderRank = CommanderRank.CAPTAIN,
    val serviceHistory: List<CareerEntry> = emptyList(),
    val friends: Set<Long> = emptySet(),
    val rivals: Set<Long> = emptySet(),
    val playerOpinion: Int = 50,
    val experience: Int = 0,
    val renowned: Boolean = false,
    val epithet: String? = null,
    val lastPromotionDay: Int = 0,
    val lastPassedOverDay: Int = 0,
    val lastAgedDay: Int = 0,
    val alive: Boolean = true,
    val consent: ConsentProfile = ConsentProfile(),
)

@Serializable
enum class CourtOffice(val label: String, val effect: String) {
    MARSHAL("Marschall", "Führung hebt Heeresmoral; loyaler Amtsinhaber erforderlich."),
    TREASURER("Schatzmeister", "Verwaltung erzeugt zusätzlichen täglichen Goldertrag."),
    SPYMASTER("Spionagemeister", "Intrige stärkt Gegenaufklärung und regionale Aufklärung."),
    AMBASSADOR("Botschafter", "Diplomatie verbessert Verhandlungen und Handelskontakte."),
    MASTER_BUILDER("Baumeister", "Belagerungskunde verkürzt laufende Bauprojekte."),
    PHYSICIAN("Hofheiler", "Medizin verbessert Verwundetenerholung und senkt Beziehungskonflikte."),
    STEWARD("Verwalter", "Verwaltung erhöht Sicherheit und Zufriedenheit."),
}

@Serializable
enum class SkillBranch(val label: String) {
    WARFARE("Kriegsführung"), LEADERSHIP("Führung"), DIPLOMACY("Diplomatie"),
    ECONOMY("Wirtschaft"), INTRIGUE("Intrige"), PERSONAL("Persönlichkeit"),
}

@Serializable
enum class PlayerPerk(val branch: SkillBranch, val label: String, val description: String, val cost: Int = 2) {
    WARFARE_HERO(SkillBranch.WARFARE, "An vorderster Front", "Persönliche Schlachtteilnahme stärkt die eigene Formation."),
    WARFARE_FEIGNED_RETREAT(SkillBranch.WARFARE, "Falscher Rückzug", "Taktischer Rückzug bewahrt Befehlsfähigkeit und bringt Taktikerfahrung."),
    WARFARE_RALLY(SkillBranch.WARFARE, "Schlachtrede", "Erhöht die Moral der Truppen im Feld und zu Hause."),
    LEADERSHIP_INSPIRING(SkillBranch.LEADERSHIP, "Inspirierende Führung", "Stärkt Moral um 3 und fördert Kommandanten."),
    LEADERSHIP_RESERVE(SkillBranch.LEADERSHIP, "Reserveführung", "Verbessert Befehlsregeneration und Reserven."),
    DIPLOMACY_ENVOY(SkillBranch.DIPLOMACY, "Gesandter", "+10 wirksame Diplomatie bei Verhandlungen."),
    DIPLOMACY_MEDIATOR(SkillBranch.DIPLOMACY, "Vermittler", "Entschärft Hof- und Beziehungskonflikte täglich."),
    ECONOMY_STEWARDSHIP(SkillBranch.ECONOMY, "Reichsverwaltung", "+40 Gold täglich, begrenzt durch Lagerkapazität."),
    ECONOMY_LOGISTICS(SkillBranch.ECONOMY, "Nachschubplanung", "Feldarmeen verbrauchen weniger Versorgung."),
    INTRIGUE_WATCH(SkillBranch.INTRIGUE, "Wachsame Augen", "Dauerhafte Grenzaufklärung und +1 Sicherheit täglich."),
    INTRIGUE_COUNTERINTELLIGENCE(SkillBranch.INTRIGUE, "Gegenaufklärung", "+15 Abwehr gegen feindliche Spionage."),
    PERSONAL_EMPATHY(SkillBranch.PERSONAL, "Empathie", "Gespräche beruhigen Konflikte stärker und erhöhen Vertrauen."),
    PERSONAL_RESILIENCE(SkillBranch.PERSONAL, "Widerstandskraft", "Gemeinsames Training heilt Konflikte und stärkt Loyalität."),
}

@Serializable
enum class SocialKind(val label: String) { FRIENDSHIP("Freundschaft"), RIVALRY("Rivalität"), ROMANCE("Romanze"), MARRIAGE("Partnerschaft") }

@Serializable
data class NpcSocialLink(val firstId: Long, val secondId: Long, val kind: SocialKind, val sinceDay: Int, val strength: Int = 25)

@Serializable
data class LegendEntry(val id: String, val commanderId: Long?, val name: String, val day: Int, val deed: String, val moraleBonus: Int = 2)

@Serializable
data class CourtState(
    val characters: List<CharacterDetails> = emptyList(),
    val offices: Map<CourtOffice, Long> = emptyMap(),
    val perks: Set<PlayerPerk> = emptySet(),
    val socialLinks: List<NpcSocialLink> = emptyList(),
    val legends: List<LegendEntry> = emptyList(),
    val lastTickDay: Int = 0,
    val lastSocialDay: Int = 0,
)

data class CourtBonuses(
    val morale: Int = 0, val gold: Int = 0, val diplomacy: Int = 0,
    val counterintelligence: Int = 0, val construction: Int = 0,
    val healing: Int = 0, val security: Int = 0,
)

@Serializable
data class FamilyMember(
    val id: String,
    val name: String,
    val bornDay: Int,
    val initialAge: Int = 0,
    val parents: List<String> = emptyList(),
    val alive: Boolean = true,
    val deathDay: Int? = null,
    val adopted: Boolean = false,
    val diplomacy: Int = 30,
    val leadership: Int = 30,
) {
    fun age(day: Int): Int = (initialAge + ((day - bornDay).coerceAtLeast(0) / 365)).coerceAtMost(150)
}

@Serializable
data class DynastyState(
    val members: List<FamilyMember> = emptyList(),
    val rulerId: String = "player",
    val heirId: String? = null,
    val regentCommanderId: Long? = null,
    val plannedBirthDay: Int? = null,
    val planningParents: List<String> = emptyList(),
    val lastTickDay: Int = 0,
    val lastFamilyPlanningDay: Int = -365,
    val legitimacy: Int = 50,
    val successionCount: Int = 0,
    val lastAgingDay: Int = 0,
    val activeSinceDay: Int? = null,
)
