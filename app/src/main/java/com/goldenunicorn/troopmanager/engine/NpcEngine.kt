package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.random.Random

/**
 * Persistent people of the world. Generation is deterministic (seeded by id), so a person is
 * created exactly once and then lives on in the save: name, look and personality never drift.
 * Presence at a place is derived from the routine plus a small, deterministic daily variation,
 * so taverns are not filled with the same mannequins at the same tables every evening.
 */
object NpcEngine {
    const val MIN_ADULT_AGE = 18

    private val maleNames = mapOf(
        Culture.HUMAN to listOf("Aldric", "Bertolf", "Cedric", "Dorian", "Ewald", "Falko", "Gerwin", "Hartmut", "Jorin", "Konrad", "Lothar", "Matthis", "Odo", "Rupert", "Tankred", "Ulf"),
        Culture.WOOD_ELF to listOf("Aerin", "Caladis", "Eldrin", "Faelar", "Ithron", "Lirael", "Maelis", "Thaliel"),
        Culture.GOLD_ELF to listOf("Aurelian", "Celebrin", "Elessar", "Galathil", "Orophin", "Saelorn"),
        Culture.WALL to listOf("Bai Long", "Chen Wu", "Han Tao", "Li Feng", "Wei Shan", "Zhao Ren"),
    )
    private val femaleNames = mapOf(
        Culture.HUMAN to listOf("Adela", "Brunhild", "Clara", "Dagny", "Elsbeth", "Frida", "Greta", "Hedda", "Ilse", "Jorinde", "Katrin", "Liesel", "Marlene", "Runa", "Selma", "Thea", "Wiebke"),
        Culture.WOOD_ELF to listOf("Arwen", "Calen", "Elowen", "Ilyana", "Lothiel", "Mirelle", "Nimrael", "Sylwen"),
        Culture.GOLD_ELF to listOf("Aerith", "Celestia", "Galadhwen", "Isilme", "Lumiel", "Saphira"),
        Culture.WALL to listOf("An Mei", "Hua Lin", "Jia Hui", "Lan Yue", "Mei Ling", "Xiu Ying"),
    )
    private val surnames = mapOf(
        Culture.HUMAN to listOf("Brandt", "Eichler", "Falkner", "Graumann", "Hollerbusch", "Kessler", "Lindqvist", "Morgentau", "Rabe", "Steinhauer", "Weidner"),
        Culture.WOOD_ELF to listOf("Blattsang", "Mondquell", "Silberast", "Taufarn", "Windlaub"),
        Culture.GOLD_ELF to listOf("Sonnenglanz", "Goldblüte", "Lichtsang", "Sternwacht"),
        Culture.WALL to listOf("vom Kranichtor", "aus Jadehain", "vom Tigerpass", "aus dem Lotusdelta"),
    )

    private val interestsPool = listOf("Bogenschießen", "alte Lieder", "Pferde", "Würfelspiel", "Sternkunde", "Kräuterkunde", "Schwertkampf", "Handel", "Geschichten", "Seefahrt", "Kochen", "Turniere", "Bücher", "Jagd", "Musik", "Schnitzerei")
    private val likesPool = listOf("ehrliche Worte", "Mut", "gutes Essen", "Humor", "Großzügigkeit", "Ruhe", "Abenteuer", "Höflichkeit", "Musik", "Bescheidenheit")
    private val dislikesPool = listOf("Prahlerei", "Lügen", "Grausamkeit", "Trunkenbolde", "Feigheit", "Geiz", "Aufdringlichkeit", "Arroganz", "Ungeduld")
    private val goalsPool = listOf(
        "eines Tages ein eigenes Gasthaus führen", "die Mörder ihres Bruders finden", "als Kriegerin Ruhm erlangen",
        "die Welt jenseits des Silbermeers sehen", "ihre verschuldete Familie freikaufen", "ein Buch über die Horden schreiben",
        "einen sicheren Ort für die Entflohenen schaffen", "im großen Turnier von Aldenburg siegen",
    )
    private val moralsPool = listOf("Hält Wort, auch wenn es wehtut.", "Sieht die Welt pragmatisch.", "Hilft Schwächeren ohne Zögern.", "Misstraut Mächtigen.", "Glaubt an zweite Chancen.")
    private val combatPool = listOf("Bogenschützin", "Schwert und Schild", "Zwillingsdolche", "Speerkämpferin", "Faustkampf", "Keine Kämpferin")

    fun rng(seed: String): Random = Random(seed.hashCode().toLong() * 31 + seed.length)

    private fun <T> Random.pick(list: List<T>): T = list[nextInt(list.size)]
    private fun Random.around(mid: Int, spread: Int) = (mid + nextInt(-spread, spread + 1)).coerceIn(0, 100)

    fun cultureFor(species: Species, random: Random): Culture = when (species) {
        Species.HUMAN -> if (random.nextInt(4) == 0) Culture.WALL else Culture.HUMAN
        Species.ELF -> if (random.nextBoolean()) Culture.WOOD_ELF else Culture.GOLD_ELF
        Species.HALF_ELF -> if (random.nextBoolean()) Culture.WOOD_ELF else Culture.HUMAN
    }

    fun randomName(gender: Gender, culture: Culture, random: Random): String {
        val first = random.pick((if (gender == Gender.FEMALE) femaleNames else maleNames).getValue(culture))
        if (culture == Culture.WALL) return first
        return "$first ${random.pick(surnames.getValue(culture))}"
    }

    /**
     * Balanced random look. Locked fields keep the given value, so the editor can randomize only
     * what the player has not decided yet.
     */
    fun randomAppearance(
        random: Random,
        gender: Gender,
        species: Species,
        base: CharacterAppearance = CharacterAppearance(),
        locked: Set<String> = emptySet(),
    ): CharacterAppearance {
        val o = AppearanceOptions
        fun <T> keep(key: String, current: T, roll: () -> T): T = if (key in locked) current else roll()
        val female = gender == Gender.FEMALE
        return CharacterAppearance(
            faceShape = keep("face", base.faceShape) { random.nextInt(o.faceShapes.size) },
            jaw = keep("face", base.jaw) { random.around(if (female) 38 else 60, 18) },
            cheekbones = keep("face", base.cheekbones) { random.around(55, 20) },
            noseSize = keep("face", base.noseSize) { random.around(if (female) 40 else 55, 18) },
            noseShape = keep("face", base.noseShape) { random.nextInt(o.noseShapes.size) },
            eyeShape = keep("eyes", base.eyeShape) { random.nextInt(o.eyeShapes.size) },
            eyeColor = keep("eyes", base.eyeColor) { random.nextInt(o.eyeColors.size) },
            browStyle = keep("eyes", base.browStyle) { if (female) random.pick(listOf(1, 3, 0)) else random.nextInt(o.browStyles.size) },
            lips = keep("mouth", base.lips) { random.around(if (female) 60 else 42, 16) },
            mouthWidth = keep("mouth", base.mouthWidth) { random.around(50, 15) },
            ears = keep("ears", base.ears) { if (species == Species.HUMAN) 0 else if (species == Species.ELF) random.pick(listOf(1, 2)) else 1 },
            skinTone = keep("skin", base.skinTone) { random.nextInt(o.skinTones.size) },
            scar = keep("marks", base.scar) { if (random.nextInt(4) == 0) random.nextInt(1, o.scars.size) else 0 },
            tattoo = keep("marks", base.tattoo) { if (random.nextInt(6) == 0) random.nextInt(1, o.tattoos.size) else 0 },
            beard = keep("beard", base.beard) { if (female || species == Species.ELF) 0 else random.nextInt(o.beards.size) },
            hairStyle = keep("hair", base.hairStyle) { if (female) random.pick(listOf(2, 3, 4, 5, 1, 6)) else random.pick(listOf(0, 1, 2, 3, 6, 7, 0)) },
            hairLength = keep("hair", base.hairLength) { if (female) random.around(70, 25) else random.around(35, 25) },
            hairColor = keep("hairColor", base.hairColor) { random.nextInt(o.hairColors.size) },
            hairTexture = keep("hair", base.hairTexture) { random.nextInt(o.hairTextures.size) },
            height = keep("body", base.height) { random.around(if (female) 42 else 58, 22) },
            build = keep("body", base.build) { random.nextInt(o.builds.size) },
            muscle = keep("body", base.muscle) { random.around(50, 30) },
            posture = keep("body", base.posture) { random.nextInt(o.postures.size) },
            clothing = keep("outfit", base.clothing) { if (female) random.pick(listOf(0, 1, 2, 3, 4)) else random.pick(listOf(0, 1, 3, 4)) },
            armor = keep("outfit", base.armor) { random.nextInt(o.armor.size) },
            cloak = keep("outfit", base.cloak) { random.nextInt(o.cloaks.size) },
            jewelry = keep("outfit", base.jewelry) { random.nextInt(o.jewelry.size) },
            weapon = keep("outfit", base.weapon) { random.nextInt(o.weapons.size) },
            primaryColor = keep("colors", base.primaryColor) { random.nextInt(o.fabricColors.size) },
            accentColor = keep("colors", base.accentColor) { random.nextInt(o.fabricColors.size) },
            expression = keep("expression", base.expression) { random.nextInt(o.expressions.size) },
            background = keep("background", base.background) { random.nextInt(o.backgrounds.size) },
        ).normalized()
    }

    fun randomPersonality(random: Random): NpcPersonality = NpcPersonality(
        warmth = random.around(55, 35), boldness = random.around(50, 35), honor = random.around(55, 35),
        humor = random.around(50, 35), curiosity = random.around(50, 35), temper = random.around(35, 30),
    )

    private fun routineFor(role: NpcRole, random: Random): NpcRoutine = when (role) {
        NpcRole.INNKEEPER -> NpcRoutine(LifePlace.TAVERN, LifePlace.TAVERN, LifePlace.TAVERN, LifePlace.TAVERN, LifePlace.TAVERN, 100)
        NpcRole.REGULAR -> NpcRoutine(LifePlace.MARKET, LifePlace.TAVERN, LifePlace.TOWN, LifePlace.TAVERN, if (random.nextBoolean()) LifePlace.TAVERN else LifePlace.AWAY, random.around(75, 15))
        NpcRole.TRAVELER -> NpcRoutine(LifePlace.AWAY, LifePlace.MARKET, LifePlace.TAVERN, LifePlace.TAVERN, LifePlace.TAVERN, random.around(45, 15))
        NpcRole.MERCENARY_CAPTAIN -> NpcRoutine(LifePlace.BARRACKS, LifePlace.TAVERN, LifePlace.BARRACKS, LifePlace.TAVERN, LifePlace.AWAY, random.around(55, 15))
        NpcRole.MERCHANT -> NpcRoutine(LifePlace.MARKET, LifePlace.MARKET, LifePlace.MARKET, LifePlace.TAVERN, LifePlace.AWAY, random.around(70, 15))
        NpcRole.NOBLE -> NpcRoutine(LifePlace.AWAY, LifePlace.AWAY, LifePlace.TOWN, LifePlace.TAVERN, LifePlace.AWAY, random.around(30, 10))
        NpcRole.BARD -> NpcRoutine(LifePlace.AWAY, LifePlace.TOWN, LifePlace.MARKET, LifePlace.TAVERN, LifePlace.TAVERN, random.around(80, 10))
        NpcRole.COMPANION -> NpcRoutine(LifePlace.MARKET, LifePlace.TOWN, LifePlace.MARKET, LifePlace.TAVERN, LifePlace.AWAY, 60)
        NpcRole.SELLSWORD -> NpcRoutine(LifePlace.BARRACKS, LifePlace.TAVERN, LifePlace.BARRACKS, LifePlace.TAVERN, LifePlace.TAVERN, 70)
        NpcRole.SMITH -> NpcRoutine(LifePlace.MARKET, LifePlace.MARKET, LifePlace.MARKET, LifePlace.TAVERN, LifePlace.AWAY, 85)
        NpcRole.HEALER -> NpcRoutine(LifePlace.MARKET, LifePlace.MARKET, LifePlace.MARKET, LifePlace.AWAY, LifePlace.AWAY, 90)
        NpcRole.SERGEANT -> NpcRoutine(LifePlace.BARRACKS, LifePlace.BARRACKS, LifePlace.BARRACKS, LifePlace.BARRACKS, LifePlace.AWAY, 100)
        NpcRole.LORD -> NpcRoutine(LifePlace.CASTLE, LifePlace.CASTLE, LifePlace.CASTLE, LifePlace.CASTLE, LifePlace.AWAY, 100)
    }

    private fun professionFor(role: NpcRole, gender: Gender, random: Random): String = when (role) {
        NpcRole.INNKEEPER -> if (gender == Gender.FEMALE) "Wirtin" else "Wirt"
        NpcRole.REGULAR -> if (gender == Gender.FEMALE) random.pick(listOf("Schmiedin", "Flößerin", "Bäckerin", "Gerberin", "Wäscherin", "Fischerin"))
            else random.pick(listOf("Schmied", "Flößer", "Bäcker", "Gerber", "Stallknecht", "Fischer", "Holzfäller"))
        NpcRole.TRAVELER -> if (gender == Gender.FEMALE) random.pick(listOf("Reisende Kriegerin", "Pilgerin", "Kartenzeichnerin", "Botin", "Wandernde Heilerin", "Schatzsucherin"))
            else random.pick(listOf("Reisender Krieger", "Pilger", "Kartenzeichner", "Bote", "Wandernder Heiler", "Schatzsucher"))
        NpcRole.MERCENARY_CAPTAIN -> if (gender == Gender.FEMALE) "Söldnerhauptfrau" else "Söldnerhauptmann"
        NpcRole.MERCHANT -> if (gender == Gender.FEMALE) random.pick(listOf("Tuchhändlerin", "Gewürzhändlerin", "Weinhändlerin"))
            else random.pick(listOf("Tuchhändler", "Gewürzhändler", "Pferdehändler"))
        NpcRole.NOBLE -> if (gender == Gender.FEMALE) "Edeldame (inkognito)" else "Edelmann (inkognito)"
        NpcRole.BARD -> if (gender == Gender.FEMALE) "Spielfrau" else "Spielmann"
        NpcRole.COMPANION -> "Kriegerin"
        NpcRole.SELLSWORD -> if (gender == Gender.FEMALE) "Söldnerin" else "Söldner"
        NpcRole.SMITH -> if (gender == Gender.FEMALE) "Schmiedin" else "Schmied"
        NpcRole.HEALER -> if (gender == Gender.FEMALE) "Heilerin" else "Heiler"
        NpcRole.SERGEANT -> if (gender == Gender.FEMALE) "Ausbilderin" else "Ausbilder"
        NpcRole.LORD -> if (gender == Gender.FEMALE) "Burgvögtin" else "Burgvogt"
    }

    private fun standingFor(role: NpcRole): String = when (role) {
        NpcRole.NOBLE -> "Hochadel"
        NpcRole.MERCHANT -> "Bürgertum"
        NpcRole.MERCENARY_CAPTAIN -> "Freie Kompanie"
        NpcRole.INNKEEPER -> "Bürgertum"
        else -> "Gemeines Volk"
    }

    /** Generates a fresh adult person. [seed] fully determines the result. */
    fun generate(
        seed: String,
        role: NpcRole,
        homeProvinceId: String,
        gender: Gender? = null,
        fixedName: String? = null,
    ): NpcIdentity {
        val r = rng(seed)
        val g = gender ?: if (r.nextInt(100) < 52) Gender.FEMALE else Gender.MALE
        val species = when (CrownCatalog.info(homeProvinceId).culture) {
            Culture.WOOD_ELF, Culture.GOLD_ELF -> if (r.nextInt(4) == 0) Species.HALF_ELF else Species.ELF
            else -> when (r.nextInt(10)) { 0 -> Species.ELF; 1, 2 -> Species.HALF_ELF; else -> Species.HUMAN }
        }
        val culture = when (species) {
            Species.HUMAN -> if (CrownCatalog.info(homeProvinceId).culture == Culture.WALL || r.nextInt(6) == 0) Culture.WALL else Culture.HUMAN
            else -> cultureFor(species, r)
        }
        val age = when (role) {
            NpcRole.INNKEEPER -> r.nextInt(34, 62)
            NpcRole.NOBLE -> r.nextInt(20, 40)
            NpcRole.COMPANION -> r.nextInt(20, 30)
            else -> r.nextInt(19, 55)
        }.coerceAtLeast(MIN_ADULT_AGE)
        val personality = randomPersonality(r)
        val town = CrownCatalog.info(homeProvinceId).name
        val name = fixedName ?: randomName(g, culture, r)
        val profession = professionFor(role, g, r)
        val romanceable = role != NpcRole.INNKEEPER && r.nextInt(100) < 70
        val attractedTo: Set<Gender> = when (r.nextInt(10)) {
            in 0..5 -> setOf(if (g == Gender.FEMALE) Gender.MALE else Gender.FEMALE)
            6, 7 -> setOf(Gender.MALE, Gender.FEMALE, Gender.DIVERSE)
            else -> setOf(g)
        }
        return NpcIdentity(
            id = seed,
            name = name,
            gender = g,
            age = age,
            species = species,
            culture = culture,
            origin = town,
            profession = profession,
            socialStanding = standingFor(role),
            backstory = "${name.substringBefore(' ')} stammt aus $town und arbeitet als $profession. " +
                "Man sagt, ${if (g == Gender.FEMALE) "sie" else "er"} sei ${personality.traits().take(2).joinToString(" und ")}.",
            appearance = randomAppearance(r, g, species),
            personality = personality,
            interests = List(2) { r.pick(interestsPool) }.distinct(),
            likes = List(2) { r.pick(likesPool) }.distinct(),
            dislikes = List(2) { r.pick(dislikesPool) }.distinct(),
            goal = r.pick(goalsPool),
            morals = r.pick(moralsPool),
            combatStyle = if (role == NpcRole.MERCENARY_CAPTAIN || role == NpcRole.COMPANION) r.pick(combatPool.dropLast(1)) else r.pick(combatPool),
            role = role,
            homeProvinceId = homeProvinceId,
            routine = routineFor(role, r),
            romanceable = romanceable,
            attractedTo = if (romanceable) attractedTo else emptySet(),
        )
    }

    /** Everyone living around one town: innkeeper, regulars, a merchant, a bard, and visitors. */
    fun populateTown(town: TownDef): List<NpcIdentity> {
        val id = town.provinceId
        val list = mutableListOf<NpcIdentity>()
        val innkeeperFemale = town.innkeeperName.split(' ').first() in setOf("Wilma", "Maris", "Grit")
        list += generate("$id:wirt", NpcRole.INNKEEPER, id, if (innkeeperFemale) Gender.FEMALE else Gender.MALE, town.innkeeperName)
            .let { it.copy(romanceable = false, attractedTo = emptySet(), backstory = "${it.firstName} führt „${town.tavernName}“ seit vielen Jahren. Wer etwas wissen will, fragt hier zuerst.") }
        listOf(NpcRole.REGULAR, NpcRole.REGULAR, NpcRole.REGULAR, NpcRole.TRAVELER, NpcRole.TRAVELER,
            NpcRole.MERCHANT, NpcRole.BARD, NpcRole.MERCENARY_CAPTAIN, NpcRole.NOBLE)
            .forEachIndexed { i, role -> list += generate("$id:${role.name.lowercase()}:$i", role, id) }
        return list
    }

    // ------------------------------------------------------------ the optional future companion

    /**
     * Fields the player may fix when designing a companion. Null means "generate fitting value".
     * Age below 18 is impossible: it is rejected by [designCompanion] and by save validation.
     */
    data class CompanionDraft(
        val name: String? = null,
        val age: Int? = null,
        val species: Species? = null,
        val culture: Culture? = null,
        val origin: String? = null,
        val profession: String? = null,
        val socialStanding: String? = null,
        val backstory: String? = null,
        val appearance: CharacterAppearance? = null,
        val personality: NpcPersonality? = null,
        val interests: List<String>? = null,
        val morals: String? = null,
        val combatStyle: String? = null,
        val goal: String? = null,
        val homeProvinceId: String? = null,
    )

    /** Creates the designed companion as an independent person. No relationship is created. */
    fun designCompanion(seed: String, draft: CompanionDraft, startProvinceId: String, playerGender: Gender): NpcIdentity {
        require(draft.age == null || draft.age >= MIN_ADULT_AGE) { "Die Gefährtin muss mindestens 18 Jahre alt sein." }
        val r = rng("companion:$seed")
        val species = draft.species ?: r.pick(Species.entries)
        val culture = draft.culture ?: cultureFor(species, r)
        val generated = generate("companion:$seed", NpcRole.COMPANION, draft.homeProvinceId ?: startProvinceId, Gender.FEMALE)
        val personality = (draft.personality ?: generated.personality).normalized()
        val name = draft.name?.trim()?.take(24)?.ifBlank { null } ?: randomName(Gender.FEMALE, culture, r)
        val look = (draft.appearance ?: randomAppearance(r, Gender.FEMALE, species)).normalized().let {
            if (species == Species.HUMAN) it else if (it.ears == 0) it.copy(ears = 1) else it
        }
        val profession = draft.profession?.ifBlank { null } ?: r.pick(listOf("Reisende Kriegerin", "Grenzläuferin", "Bogenschützin", "Heilerin", "Kartenzeichnerin", "Söldnerin"))
        return generated.copy(
            id = "companion",
            name = name,
            age = (draft.age ?: generated.age).coerceIn(MIN_ADULT_AGE, 120),
            species = species,
            culture = culture,
            origin = draft.origin?.ifBlank { null } ?: generated.origin,
            profession = profession,
            socialStanding = draft.socialStanding?.ifBlank { null } ?: "Freie Frau",
            backstory = draft.backstory?.ifBlank { null }
                ?: "${name.substringBefore(' ')} ist $profession und auf der Durchreise. Sie spricht wenig über ihre Vergangenheit – nur, dass sie ${generated.goal} will.",
            appearance = look,
            personality = personality,
            interests = draft.interests?.filter { it.isNotBlank() }?.take(4)?.ifEmpty { null } ?: generated.interests,
            morals = draft.morals?.ifBlank { null } ?: generated.morals,
            combatStyle = draft.combatStyle?.ifBlank { null } ?: generated.combatStyle,
            goal = draft.goal?.ifBlank { null } ?: generated.goal,
            role = NpcRole.COMPANION,
            // She travels: a few days near the player's start town, so an early meeting is plausible.
            homeProvinceId = startProvinceId,
            routine = NpcRoutine(LifePlace.MARKET, LifePlace.TOWN, LifePlace.MARKET, LifePlace.TAVERN, LifePlace.AWAY, 75),
            // She can be courted, but whether she wants that is decided in play, never at creation.
            romanceable = true,
            attractedTo = setOf(playerGender),
            designedByPlayer = true,
            relation = NpcRelation(),
        )
    }

    // ------------------------------------------------------------ presence

    private fun dailyRoll(npc: NpcIdentity, day: Int, slot: TimeSlot): Int =
        Math.floorMod((npc.id.hashCode() * 31 + day * 17 + slot.ordinal * 7), 100)

    /**
     * Where a person is right now. Routines bend with the day: travelers move on, regulars
     * sometimes stay home, the tavern gets fuller in the evening. The designed companion stays
     * around the start town during the first days so an early, natural meeting is possible.
     */
    fun placeOf(npc: NpcIdentity, day: Int, slot: TimeSlot, world: GameState? = null): Pair<String, LifePlace> {
        if (npc.departed) return npc.homeProvinceId to LifePlace.AWAY
        // Followers go where the player goes.
        if (world != null && world.life.inParty(npc.id)) return world.life.provinceId to world.life.place
        val usual = npc.routine.at(slot)
        val roll = dailyRoll(npc, day, slot)
        if (npc.role == NpcRole.TRAVELER || npc.role == NpcRole.NOBLE) {
            // Travelers are only in town on some days.
            val inTown = Math.floorMod(npc.id.hashCode() + day * 13, 5) < (if (npc.role == NpcRole.NOBLE) 1 else 3)
            if (!inTown) return npc.homeProvinceId to LifePlace.AWAY
        }
        if (npc.designedByPlayer) {
            // Days 1..4: guaranteed in the start town in the evening on day 1 or 2, otherwise at market/tavern.
            if (day <= 4 && slot == TimeSlot.EVENING && (day >= 2 || roll < 60)) return npc.homeProvinceId to LifePlace.TAVERN
        }
        val war = world?.let { LifeEngine.threatened(it, npc.homeProvinceId) } ?: false
        if (war && npc.role == NpcRole.MERCENARY_CAPTAIN && slot != TimeSlot.NIGHT) return npc.homeProvinceId to LifePlace.TAVERN
        val place = if (roll < npc.routine.regularity) usual else when (usual) {
            LifePlace.TAVERN -> LifePlace.AWAY
            LifePlace.AWAY -> if (slot == TimeSlot.EVENING || slot == TimeSlot.NIGHT) LifePlace.TAVERN else LifePlace.MARKET
            else -> if (roll % 2 == 0) LifePlace.TAVERN else LifePlace.AWAY
        }
        // Rain, storm and snow drive the townsfolk into the tavern in the afternoon and evening.
        if ((slot == TimeSlot.AFTERNOON || slot == TimeSlot.EVENING) && place in setOf(LifePlace.TOWN, LifePlace.MARKET) &&
            npc.role in setOf(NpcRole.REGULAR, NpcRole.TRAVELER) && TownWeather.foul(TownWeather.today(day, npc.homeProvinceId)))
            return npc.homeProvinceId to LifePlace.TAVERN
        return npc.homeProvinceId to place
    }

    fun presentAt(state: GameState, provinceId: String, place: LifePlace): List<NpcIdentity> =
        state.life.npcs.filter { placeOf(it, state.day, state.life.timeSlot, state) == (provinceId to place) }

    // ------------------------------------------------------------ relation helpers

    fun stageFor(r: NpcRelation): SocialStage {
        if (r.stage.romantic) return r.stage
        return when {
            r.affinity >= 70 && r.trust >= 60 -> SocialStage.CLOSE_FRIEND
            r.affinity >= 40 && r.trust >= 30 -> SocialStage.FRIEND
            r.timesMet > 0 -> SocialStage.ACQUAINTANCE
            else -> SocialStage.STRANGER
        }
    }

    fun remember(npc: NpcIdentity, key: String, day: Int, text: String, weight: Int = 1): NpcIdentity =
        if (npc.remembers(key) && !key.startsWith("talk")) npc
        else npc.copy(memories = (npc.memories + NpcMemory(key, day, text, weight)).takeLast(24))

    fun validate(npc: NpcIdentity) {
        require(npc.age >= MIN_ADULT_AGE) { "Alle Personen müssen erwachsen sein." }
        require(npc.name.isNotBlank() && npc.id.isNotBlank()) { "Ungültige Person." }
        require(npc.relation.affinity in -100..100 && npc.relation.trust in 0..100 && npc.relation.attraction in 0..100) { "Ungültige Beziehung." }
        require(npc.intoxication in 0..100) { "Ungültiger Rauschwert." }
    }
}
