package com.goldenunicorn.troopmanager.model

/*
 * Data-driven, offline dialogue content. Each topic declares when it is available, how hard it is,
 * which personality traits help or hurt, and what success or failure does to the relationship.
 * The engine ([com.goldenunicorn.troopmanager.engine.DialogueEngine]) only interprets this data,
 * so new conversations are added here without touching rules.
 *
 * Placeholders: {player} {npc} {town} {interest} {goal} {origin} {backstory} {rumor} {profession}
 * {like} {dislike} {deedStory} {petition} {tourneyInfo} {crownNews} {townFlavor}.
 */

enum class Trait { WARMTH, BOLDNESS, HONOR, HUMOR, CURIOSITY, TEMPER }

enum class TopicKind(val label: String) {
    TALK("Gespräch"), FRIENDLY("Freundlich"), ROMANCE("Romantik"), CONFLICT("Konflikt"), BUSINESS("Geschäft"),
}

data class TopicReq(
    val minStage: SocialStage? = null,
    val stages: Set<SocialStage>? = null,
    val minAffinity: Int? = null,
    val minTrust: Int? = null,
    val minAttraction: Int? = null,
    /** Requires that this person is open to romance with the player and has not set a boundary. */
    val romance: Boolean = false,
    val roles: Set<NpcRole>? = null,
    val slots: Set<TimeSlot>? = null,
    val maxPlayerIntox: Int? = null,
    val maxNpcIntox: Int? = null,
    val needsMemory: String? = null,
    val lacksMemory: String? = null,
    val cost: Int = 0,
    val needsRoom: Boolean = false,
    val noOtherPartner: Boolean = false,
    val minTimesMet: Int? = null,
    /** Only after the person was hurt or there is open rivalry. */
    val needsGrievance: Boolean = false,
    /** A deed the player must have done: "ruler", "knight", "tourney", "freed" or "any". */
    val deed: String? = null,
)

data class TopicEffect(
    val affinity: Int = 0,
    val trust: Int = 0,
    val attraction: Int = 0,
    val rivalry: Int = 0,
    val memoryKey: String? = null,
    /** Written from the person's view, addressed to the player: "du hast mir ... ausgegeben". */
    val memoryText: String? = null,
    val memoryWeight: Int = 1,
    val stage: SocialStage? = null,
    val npcDrink: Int = 0,
    val renown: Int = 0,
    val boundary: Boolean = false,
    val intimate: Boolean = false,
    val brawl: Boolean = false,
    val passSlots: Int = 0,
    val clearGrievance: Boolean = false,
    /** Teaches the player: attribute name (sword, bow, riding, leadership, tactics, diplomacy) +1. */
    val teaches: String? = null,
)

data class DialogueTopic(
    val id: String,
    val kind: TopicKind,
    val label: String,
    val playerLine: String,
    val req: TopicReq = TopicReq(),
    /** 0 = always succeeds. Otherwise compared with the social score. */
    val difficulty: Int = 0,
    val traits: Map<Trait, Int> = emptyMap(),
    /** Consent topics run the explicit consent decision instead of a social check. */
    val consent: Boolean = false,
    val success: TopicEffect = TopicEffect(),
    val failure: TopicEffect = TopicEffect(),
    val successLines: List<String>,
    val coolSuccessLines: List<String> = emptyList(),
    val failLines: List<String> = emptyList(),
    val oncePerSlot: Boolean = true,
    /** Handled by a dedicated engine instead of the generic social check: invite, dismiss, hire, duel, gift. */
    val special: String? = null,
)

object DialogueCatalog {
    private val romanticStages = setOf(SocialStage.FLIRT, SocialStage.LOVER, SocialStage.PARTNER, SocialStage.ENGAGED, SocialStage.MARRIED)

    val topics: List<DialogueTopic> = listOf(
        // ------------------------------------------------------------ talk
        DialogueTopic(
            "smalltalk", TopicKind.TALK, "Wie läuft's so?", "„Na, wie läuft's?“",
            success = TopicEffect(affinity = 3, trust = 1),
            successLines = listOf(
                "„Ach, man schlägt sich durch. Heute war viel los in {town}.“",
                "„Besser, seit ich sitze. Und du, {player}?“",
                "„Frag nicht. Aber danke, dass du fragst.“",
            ),
            coolSuccessLines = listOf("„Es läuft.“ Ein knappes Nicken.", "„Gut genug.“"),
        ),
        DialogueTopic(
            "ask_origin", TopicKind.TALK, "Woher kommst du?", "„Du bist nicht von hier, oder? Woher kommst du?“",
            req = TopicReq(lacksMemory = "told_origin"),
            difficulty = 25, traits = mapOf(Trait.WARMTH to 20, Trait.CURIOSITY to 10),
            success = TopicEffect(affinity = 4, trust = 6, memoryKey = "told_origin", memoryText = "ich habe dir erzählt, woher ich komme"),
            failure = TopicEffect(affinity = -1),
            successLines = listOf("„Aus {origin}.“ Ein kurzes Lächeln. „{backstory}“"),
            failLines = listOf("„Von da und dort.“ Mehr verrät {npc} nicht – noch nicht."),
        ),
        DialogueTopic(
            "ask_goal", TopicKind.TALK, "Was treibt dich an?", "„Was willst du eigentlich vom Leben?“",
            req = TopicReq(minStage = SocialStage.ACQUAINTANCE, minTrust = 15, lacksMemory = "told_goal"),
            difficulty = 40, traits = mapOf(Trait.WARMTH to 15, Trait.HONOR to 10),
            success = TopicEffect(affinity = 5, trust = 10, memoryKey = "told_goal", memoryText = "ich habe dir von meinem Ziel erzählt"),
            failure = TopicEffect(trust = -2),
            successLines = listOf("Eine lange Pause. „Ich will {goal}. Lach nicht.“", "„Ehrlich? Ich will {goal}. Das hab ich noch kaum jemandem gesagt.“"),
            failLines = listOf("„Das geht dich nichts an.“ Nicht unfreundlich – aber endgültig."),
        ),
        DialogueTopic(
            "talk_interest", TopicKind.TALK, "Über {interest} reden", "„Ich hab gehört, du verstehst etwas von {interest}?“",
            req = TopicReq(minStage = SocialStage.ACQUAINTANCE),
            difficulty = 30, traits = mapOf(Trait.CURIOSITY to 25, Trait.WARMTH to 10),
            success = TopicEffect(affinity = 7, trust = 3, attraction = 2, memoryKey = "talked_interest", memoryText = "wir haben lange über meine Leidenschaft geredet", memoryWeight = 2),
            failure = TopicEffect(affinity = 1),
            successLines = listOf("Die Augen von {npc} leuchten. „{interest}? Endlich jemand, der fragt!“ Das Gespräch dauert, bis die Kerze halb heruntergebrannt ist."),
            failLines = listOf("„Ein wenig.“ {npc} scheint heute nicht in Stimmung zu sein."),
        ),
        DialogueTopic(
            "rumor", TopicKind.TALK, "Gibt es Neuigkeiten?", "„Was erzählt man sich so?“",
            success = TopicEffect(affinity = 1),
            successLines = listOf("„{rumor}“", "Ein Blick über die Schulter, dann leiser: „{rumor}“"),
        ),
        DialogueTopic(
            "joke", TopicKind.FRIENDLY, "Einen Witz erzählen", "Du erzählst den von dem Ork, der eine Brücke kaufen wollte.",
            difficulty = 45, traits = mapOf(Trait.HUMOR to 35, Trait.TEMPER to -10),
            success = TopicEffect(affinity = 6, attraction = 2, memoryKey = "joke", memoryText = "du hast mich mit deinem Ork-Witz zum Lachen gebracht"),
            failure = TopicEffect(affinity = -3),
            successLines = listOf("{npc} prustet ins Bier. „Bei allen Göttern, der war gut!“", "Ein ehrliches Lachen. „Den muss ich mir merken.“"),
            failLines = listOf("Stille. {npc} hebt eine Braue. „…Aha.“", "„Den kannte ich schon. Den kennt jeder.“"),
        ),
        DialogueTopic(
            "compliment", TopicKind.FRIENDLY, "Ein Kompliment machen", "„Du hast ein gutes Auge – und eine ruhige Hand. Das merkt man.“",
            req = TopicReq(minStage = SocialStage.ACQUAINTANCE),
            difficulty = 35, traits = mapOf(Trait.WARMTH to 25, Trait.HONOR to -5),
            success = TopicEffect(affinity = 5, attraction = 3),
            failure = TopicEffect(affinity = -2),
            successLines = listOf("„Das ist… nett von dir.“ Ein verlegenes Lächeln.", "„Schmeichler. Aber ein ehrlicher, glaube ich.“"),
            failLines = listOf("„Spar dir das. Was willst du?“"),
        ),
        DialogueTopic(
            "buy_drink", TopicKind.FRIENDLY, "Einen Met ausgeben (4 Gold)", "Du winkst dem Wirt: „Einen Met für {npc}, bitte.“",
            req = TopicReq(cost = 4, maxNpcIntox = 60, roles = NpcRole.entries.toSet() - NpcRole.INNKEEPER),
            success = TopicEffect(affinity = 6, trust = 2, npcDrink = 18, memoryKey = "drink_bought", memoryText = "du hast mir einen Met ausgegeben"),
            successLines = listOf("„Auf dich, {player}!“ Die Becher stoßen klirrend zusammen.", "„Na, da sag ich nicht nein.“"),
        ),
        DialogueTopic(
            "help", TopicKind.FRIENDLY, "Hilfe anbieten", "„Wenn du bei deinem Vorhaben Hilfe brauchst – sag Bescheid.“",
            req = TopicReq(needsMemory = "told_goal", lacksMemory = "help_offered"),
            difficulty = 20, traits = mapOf(Trait.HONOR to 15, Trait.WARMTH to 10),
            success = TopicEffect(affinity = 10, trust = 14, memoryKey = "help_offered", memoryText = "du hast mir Hilfe angeboten, ohne etwas zu verlangen", memoryWeight = 3),
            failure = TopicEffect(trust = 2),
            successLines = listOf("{npc} schaut dich lange an. „Das… bedeutet mir mehr, als du denkst.“"),
            failLines = listOf("„Ich komme allein zurecht. Aber danke.“"),
        ),
        DialogueTopic(
            "armwrestle", TopicKind.FRIENDLY, "Zum Armdrücken herausfordern", "Du stellst den Ellbogen auf den Tisch. „Lust auf eine Runde?“",
            req = TopicReq(minStage = SocialStage.ACQUAINTANCE, maxPlayerIntox = 80),
            difficulty = 50, traits = mapOf(Trait.BOLDNESS to 20, Trait.HUMOR to 10),
            success = TopicEffect(affinity = 5, attraction = 3, renown = 1, memoryKey = "armwrestle_won", memoryText = "du hast mich beim Armdrücken geschlagen"),
            failure = TopicEffect(affinity = 3, memoryKey = "armwrestle_lost", memoryText = "ich habe dich beim Armdrücken besiegt"),
            successLines = listOf("Ein Ruck – und der Arm von {npc} liegt auf dem Holz. „Revanche. Irgendwann.“"),
            failLines = listOf("Dein Arm klatscht auf die Platte. {npc} grinst breit. „Übung, {player}. Übung.“"),
        ),
        // ------------------------------------------------------------ romance (never automatic)
        DialogueTopic(
            "flirt", TopicKind.ROMANCE, "Flirten", "Du lächelst. „Ist der Platz neben dir noch frei – oder wartest du auf jemand Besseren?“",
            req = TopicReq(romance = true, minStage = SocialStage.ACQUAINTANCE, maxPlayerIntox = 70),
            difficulty = 50, traits = mapOf(Trait.WARMTH to 15, Trait.HUMOR to 15, Trait.BOLDNESS to 10),
            success = TopicEffect(attraction = 8, affinity = 3, memoryKey = "flirted", memoryText = "du hast mit mir geflirtet"),
            failure = TopicEffect(affinity = -2),
            successLines = listOf("{npc} lacht leise. „Vielleicht hab ich ja auf genau dich gewartet. Setz dich.“", "Ein Blick, der etwas zu lang dauert. „Du bist frech, {player}. Gefällt mir.“"),
            failLines = listOf("„Netter Versuch.“ {npc} wendet sich wieder dem Becher zu.", "„Heute nicht, {player}.“ Freundlich, aber klar."),
        ),
        DialogueTopic(
            "date", TopicKind.ROMANCE, "Zu einem Abendspaziergang einladen", "„Begleitest du mich ein Stück am Fluss entlang? Die Laternen sind schön um diese Zeit.“",
            req = TopicReq(romance = true, stages = romanticStages, slots = setOf(TimeSlot.AFTERNOON, TimeSlot.EVENING), maxPlayerIntox = 50),
            difficulty = 40, traits = mapOf(Trait.WARMTH to 15, Trait.CURIOSITY to 10),
            success = TopicEffect(attraction = 10, trust = 8, affinity = 4, passSlots = 1, memoryKey = "date", memoryText = "wir sind abends zusammen spazieren gegangen", memoryWeight = 3),
            failure = TopicEffect(affinity = -1),
            successLines = listOf("Ihr geht, bis die Glocken läuten. Ihr redet über alles und nichts, und es ist leicht."),
            failLines = listOf("„Ein andermal. Ich bin müde.“"),
        ),
        DialogueTopic(
            "kiss", TopicKind.ROMANCE, "Einen Kuss wagen", "Du kommst näher und hältst inne – eine Frage ohne Worte.",
            req = TopicReq(romance = true, stages = setOf(SocialStage.FLIRT, SocialStage.LOVER, SocialStage.PARTNER), minAttraction = 45, minTrust = 20, maxPlayerIntox = 50),
            consent = true,
            success = TopicEffect(attraction = 10, affinity = 4, stage = SocialStage.LOVER, memoryKey = "kiss", memoryText = "wir haben uns geküsst", memoryWeight = 3),
            failure = TopicEffect(attraction = -3),
            successLines = listOf("{npc} schließt den Abstand. Der Kuss schmeckt nach Met und Kaminrauch."),
            failLines = listOf("{npc} lehnt sich sanft zurück. „Nicht so schnell, {player}.“"),
        ),
        DialogueTopic(
            "night", TopicKind.ROMANCE, "Die Nacht gemeinsam verbringen?", "Leise: „Mein Zimmer ist oben. Möchtest du mitkommen?“",
            req = TopicReq(romance = true, stages = setOf(SocialStage.LOVER, SocialStage.PARTNER, SocialStage.ENGAGED, SocialStage.MARRIED),
                minAttraction = 60, minTrust = 30, slots = setOf(TimeSlot.EVENING, TimeSlot.NIGHT), maxPlayerIntox = 45, maxNpcIntox = 40, needsRoom = true),
            consent = true,
            success = TopicEffect(attraction = 6, trust = 6, affinity = 5, intimate = true, memoryKey = "night", memoryText = "wir haben eine Nacht miteinander verbracht", memoryWeight = 3),
            failure = TopicEffect(),
            successLines = listOf("{npc} nimmt deine Hand. Ihr steigt gemeinsam die knarrende Treppe hinauf."),
            failLines = listOf("„Heute nicht.“ {npc} drückt kurz deine Hand. „Aber frag mich nicht, ob ich es mir überlege. Ich sag's dir, wenn.“"),
        ),
        DialogueTopic(
            "commit", TopicKind.ROMANCE, "Eine feste Beziehung vorschlagen", "„Ich will nicht nur ab und zu mit dir zusammen sein. Ich will dich an meiner Seite.“",
            req = TopicReq(romance = true, stages = setOf(SocialStage.LOVER), minTrust = 55, minAttraction = 60, noOtherPartner = true),
            consent = true,
            success = TopicEffect(trust = 10, affinity = 8, stage = SocialStage.PARTNER, memoryKey = "commit", memoryText = "du hast mich gebeten, an deiner Seite zu sein", memoryWeight = 4),
            successLines = listOf("{npc} schweigt lange. Dann: „Ja. Ich glaube, das will ich auch.“"),
            failLines = listOf("„Ich mag, was wir haben. Aber mehr – dafür bin ich noch nicht bereit.“"),
        ),
        DialogueTopic(
            "propose", TopicKind.ROMANCE, "Einen Heiratsantrag machen", "Du nimmst die Hand von {npc}. „Willst du mich heiraten?“",
            req = TopicReq(romance = true, stages = setOf(SocialStage.PARTNER), minTrust = 75, minAffinity = 65, minTimesMet = 8),
            consent = true,
            success = TopicEffect(trust = 10, affinity = 10, stage = SocialStage.ENGAGED, memoryKey = "proposal", memoryText = "du hast um meine Hand angehalten", memoryWeight = 5),
            successLines = listOf("Tränen, Lachen, ein Ja – in dieser Reihenfolge. Die halbe Taverne jubelt."),
            failLines = listOf("„Frag mich wieder, wenn wir mehr zusammen erlebt haben.“"),
        ),
        DialogueTopic(
            "wedding", TopicKind.ROMANCE, "Die Hochzeit feiern", "„Lass uns nicht länger warten. Morgen, im Tempel – und danach hier, mit allen.“",
            req = TopicReq(romance = true, stages = setOf(SocialStage.ENGAGED), minTrust = 75),
            consent = true,
            success = TopicEffect(affinity = 10, trust = 10, stage = SocialStage.MARRIED, renown = 5, memoryKey = "wedding", memoryText = "wir haben geheiratet", memoryWeight = 5),
            successLines = listOf("Ein schlichter Tempel, geliehene Kleider, ein Fest bis in die Nacht. Ihr seid verheiratet."),
            failLines = listOf("„Noch nicht. Gib mir Zeit.“"),
        ),
        DialogueTopic(
            "breakup", TopicKind.ROMANCE, "Die Beziehung beenden", "„Ich glaube, wir sollten getrennte Wege gehen.“",
            req = TopicReq(stages = romanticStages),
            success = TopicEffect(affinity = -25, trust = -20, attraction = -20, stage = SocialStage.FRIEND, memoryKey = "breakup", memoryText = "du hast dich von mir getrennt", memoryWeight = -3),
            successLines = listOf("{npc} nickt langsam. „Wenn du meinst.“ Die Stimme bleibt fest. Die Augen nicht."),
            coolSuccessLines = listOf("„Gut. Dann weiß ich, woran ich bin.“"),
        ),
        // ------------------------------------------------------------ what each kind of person knows (milestone 3)
        DialogueTopic(
            "local_history", TopicKind.TALK, "Erzähl mir von {town}", "„Was ist das hier eigentlich für ein Ort?“",
            req = TopicReq(roles = setOf(NpcRole.INNKEEPER, NpcRole.REGULAR), lacksMemory = "told_town"),
            success = TopicEffect(affinity = 4, trust = 3, memoryKey = "told_town", memoryText = "ich habe dir von unserer Stadt erzählt"),
            successLines = listOf("„{townFlavor}“ {npc} lehnt sich zurück. „Und wer hier lange genug sitzt, hört alles.“"),
        ),
        DialogueTopic(
            "tactics_talk", TopicKind.TALK, "Was macht einen guten Hauptmann aus?", "„Du führst Leute. Worauf kommt es an?“",
            req = TopicReq(roles = setOf(NpcRole.MERCENARY_CAPTAIN, NpcRole.SERGEANT), minStage = SocialStage.ACQUAINTANCE, lacksMemory = "taught_tactics"),
            difficulty = 30, traits = mapOf(Trait.WARMTH to 10, Trait.HONOR to 10),
            success = TopicEffect(affinity = 4, trust = 4, teaches = "tactics", memoryKey = "taught_tactics", memoryText = "ich habe dir beigebracht, wie man Leute führt", memoryWeight = 2),
            failure = TopicEffect(affinity = 1),
            successLines = listOf("„Nie die Flanke offen lassen. Nie den Rückzug vergessen. Und nie die Leute hungern lassen.“ (+1 Taktik)"),
            failLines = listOf("„Das lernt man nicht beim Bier. Komm wieder, wenn du Blut gesehen hast.“"),
        ),
        DialogueTopic(
            "politics", TopicKind.TALK, "Wie steht es um die Kronen?", "„Ihr seht nicht aus wie jemand, der nur auf der Durchreise ist. Was hört man aus den Höfen?“",
            req = TopicReq(roles = setOf(NpcRole.NOBLE), minTrust = 15, lacksMemory = "told_politics"),
            difficulty = 45, traits = mapOf(Trait.CURIOSITY to 15, Trait.HONOR to -10),
            success = TopicEffect(trust = 6, teaches = "diplomacy", memoryKey = "told_politics", memoryText = "ich habe dir mehr über die Höfe erzählt, als ich sollte", memoryWeight = 2),
            failure = TopicEffect(trust = -3),
            successLines = listOf("Ein Blick zur Tür, dann leise: „{crownNews}“ (+1 Diplomatie)"),
            failLines = listOf("„Ich weiß nicht, wovon Ihr sprecht. Ich bin Tuchhändler.“"),
        ),
        DialogueTopic(
            "learn_song", TopicKind.FRIENDLY, "Bring mir ein Lied bei", "„Das Lied von vorhin – bringst du es mir bei?“",
            req = TopicReq(roles = setOf(NpcRole.BARD), lacksMemory = "taught_song"),
            difficulty = 25, traits = mapOf(Trait.HUMOR to 15, Trait.WARMTH to 10),
            success = TopicEffect(affinity = 8, teaches = "diplomacy", memoryKey = "taught_song", memoryText = "ich habe dir das Lied von der letzten Mauer beigebracht", memoryWeight = 2),
            successLines = listOf("Eine Stunde, drei Strophen und viel Gelächter später kannst du es halbwegs. (+1 Diplomatie)"),
            failLines = listOf("„Nicht heute. Meine Finger sind steif.“"),
        ),
        DialogueTopic(
            "trade_tips", TopicKind.BUSINESS, "Wo lohnt sich der Handel?", "„Du kennst die Märkte. Wo wird gerade Geld verdient?“",
            req = TopicReq(roles = setOf(NpcRole.MERCHANT), lacksMemory = "trade_tips"),
            success = TopicEffect(affinity = 3, memoryKey = "trade_tips", memoryText = "ich habe dir einen Handelstipp gegeben"),
            successLines = listOf("„{tradeTip}“"),
        ),
        DialogueTopic(
            "ask_travels", TopicKind.TALK, "Wen hast du unterwegs getroffen?", "„Du kommst viel herum. Wer ist dir auf der Straße begegnet?“",
            req = TopicReq(roles = setOf(NpcRole.TRAVELER, NpcRole.BARD, NpcRole.MERCHANT)),
            success = TopicEffect(affinity = 2),
            successLines = listOf("„{travelRumor}“"),
        ),
        DialogueTopic(
            "ask_people", TopicKind.TALK, "Wer gehört hier zu wem?", "„Erzähl mal – wer versteht sich hier mit wem, und wer nicht?“",
            req = TopicReq(minStage = SocialStage.ACQUAINTANCE, roles = setOf(NpcRole.INNKEEPER, NpcRole.REGULAR, NpcRole.BARD)),
            success = TopicEffect(affinity = 1),
            successLines = listOf("„{bondGossip}“"),
        ),
        // ------------------------------------------------------------ road and blade (milestone 2)
        DialogueTopic(
            "invite", TopicKind.FRIENDLY, "Begleite mich auf meinen Wegen", "„Zieh mit mir. Ich kann jemanden wie dich gebrauchen – und ich mag deine Gesellschaft.“",
            req = TopicReq(minStage = SocialStage.ACQUAINTANCE, minTrust = 20,
                roles = setOf(NpcRole.REGULAR, NpcRole.TRAVELER, NpcRole.MERCENARY_CAPTAIN, NpcRole.BARD, NpcRole.COMPANION)),
            special = "invite", successLines = listOf(""),
        ),
        DialogueTopic(
            "dismiss", TopicKind.FRIENDLY, "Unsere Wege trennen sich hier", "„Ich glaube, hier trennen sich unsere Wege.“",
            special = "dismiss", successLines = listOf(""), oncePerSlot = false,
        ),
        DialogueTopic(
            "hire", TopicKind.BUSINESS, "Söldner anwerben (20 Gold, 2 Gold/Tag)", "„Ich brauche eine Klinge. Wen kannst du entbehren?“",
            req = TopicReq(roles = setOf(NpcRole.MERCENARY_CAPTAIN)), special = "hire", successLines = listOf(""), oncePerSlot = false,
        ),
        DialogueTopic(
            "duel", TopicKind.CONFLICT, "Zum Übungsduell fordern", "„Lust, die Klingen zu kreuzen? Bis zum ersten Blut, nicht weiter.“",
            req = TopicReq(minStage = SocialStage.ACQUAINTANCE, maxPlayerIntox = 60,
                roles = setOf(NpcRole.MERCENARY_CAPTAIN, NpcRole.SELLSWORD, NpcRole.TRAVELER, NpcRole.COMPANION)),
            special = "duel", successLines = listOf(""),
        ),
        // ------------------------------------------------------------ conflict
        DialogueTopic(
            "apologize", TopicKind.CONFLICT, "Dich entschuldigen", "„Es tut mir leid. Wirklich.“",
            req = TopicReq(needsGrievance = true),
            difficulty = 45, traits = mapOf(Trait.WARMTH to 20, Trait.HONOR to -10, Trait.TEMPER to -15),
            success = TopicEffect(affinity = 10, trust = 8, rivalry = -15, clearGrievance = true, memoryKey = "apology", memoryText = "du hast dich ehrlich entschuldigt", memoryWeight = 2),
            failure = TopicEffect(affinity = -2),
            successLines = listOf("Ein langes Ausatmen. „Na gut. Aber ich vergesse es nicht ganz.“"),
            failLines = listOf("„Worte kosten nichts, {player}.“"),
        ),
        DialogueTopic(
            "provoke", TopicKind.CONFLICT, "Provozieren", "„Mit so einem Gesicht würde ich auch in der Ecke sitzen.“",
            req = TopicReq(roles = NpcRole.entries.toSet() - NpcRole.INNKEEPER - NpcRole.COMPANION),
            difficulty = 55, traits = mapOf(Trait.TEMPER to -30, Trait.HUMOR to 15),
            success = TopicEffect(affinity = -8, rivalry = 15, memoryKey = "provoked", memoryText = "du hast mich vor allen beleidigt", memoryWeight = -2),
            failure = TopicEffect(affinity = -15, rivalry = 25, brawl = true, memoryKey = "brawl", memoryText = "wir haben uns in der Taverne geprügelt", memoryWeight = -3),
            successLines = listOf("{npc} lässt sich nicht reizen. „Geh deinen Rausch ausschlafen.“"),
            failLines = listOf("Ein Stuhl kippt. {npc} ist schneller auf den Beinen, als du denkst –"),
        ),
        DialogueTopic(
            "talk_weather", TopicKind.TALK, "Übers Wetter reden", "„Was für ein Wetter, was?“",
            success = TopicEffect(affinity = 1),
            successLines = listOf("„{weather}“"),
        ),
        // Milestone 5: deeds, the lord, a ruler among the people.
        DialogueTopic(
            "brag", TopicKind.FRIENDLY, "Von deinen Taten erzählen", "„Hast du schon gehört, was mir passiert ist?“",
            req = TopicReq(deed = "any", minStage = SocialStage.ACQUAINTANCE, lacksMemory = "bragged"),
            difficulty = 30, traits = mapOf(Trait.BOLDNESS to 10, Trait.HUMOR to 5, Trait.HONOR to -10),
            success = TopicEffect(affinity = 5, trust = 2, renown = 1, memoryKey = "bragged", memoryText = "du hast mir erzählt, wie {deedShort}"),
            failure = TopicEffect(affinity = -3, memoryKey = "bragged", memoryText = "du hast vor mir angegeben"),
            successLines = listOf("Du erzählst – {deedStory} {npc} hört mit leuchtenden Augen zu. „Das muss ich weitererzählen.“"),
            coolSuccessLines = listOf("Du erzählst – {deedStory} {npc} nickt anerkennend. „Respekt.“"),
            failLines = listOf("{npc} verdreht die Augen. „Ja, ja. Und ich hab einen Drachen mit bloßen Händen erwürgt.“"),
        ),
        DialogueTopic(
            "realm_counsel", TopicKind.TALK, "Was braucht die Grenze?", "„Ihr kennt die Lande besser als jeder andere. Was braucht die Grenze wirklich?“",
            req = TopicReq(roles = setOf(NpcRole.LORD), minStage = SocialStage.ACQUAINTANCE, lacksMemory = "lord_counsel"),
            difficulty = 35, traits = mapOf(Trait.HONOR to 10, Trait.CURIOSITY to 10),
            success = TopicEffect(trust = 6, affinity = 3, teaches = "leadership", memoryKey = "lord_counsel", memoryText = "ich habe dir gesagt, was die Grenze braucht", memoryWeight = 2),
            failure = TopicEffect(trust = -2),
            successLines = listOf("„{crownNews} Wer Leute führen will, muss zuerst wissen, wofür sie sterben.“ (+1 Führung)"),
            failLines = listOf("„Das ist eine Frage für Leute mit mehr Verantwortung als Ihr.“"),
        ),
        DialogueTopic(
            "ask_tourney", TopicKind.TALK, "Wann ist das nächste Turnier?", "„Gibt es bald ein Turnier?“",
            req = TopicReq(roles = setOf(NpcRole.LORD, NpcRole.SERGEANT, NpcRole.MERCENARY_CAPTAIN, NpcRole.NOBLE, NpcRole.SELLSWORD, NpcRole.INNKEEPER)),
            success = TopicEffect(affinity = 1),
            successLines = listOf("„{tourneyInfo}“"),
        ),
        DialogueTopic(
            "petition", TopicKind.BUSINESS, "Was bedrückt die Leute hier?", "„Ich bin nicht nur zum Trinken hier. Was fehlt euch?“",
            req = TopicReq(deed = "ruler", roles = setOf(NpcRole.REGULAR, NpcRole.INNKEEPER, NpcRole.MERCHANT, NpcRole.SMITH, NpcRole.HEALER), lacksMemory = "petition_heard", cost = 10),
            success = TopicEffect(affinity = 8, trust = 6, renown = 2, memoryKey = "petition_heard", memoryText = "du hast dir angehört, was uns fehlt, und geholfen", memoryWeight = 3),
            successLines = listOf("{npc} zögert, dann: „{petition}“ Du lässt zehn Gold aus der Schatulle da. Es spricht sich herum."),
        ),
    )

    fun topic(id: String): DialogueTopic? = topics.firstOrNull { it.id == id }
        ?: id.removePrefix("gift_").takeIf { id.startsWith("gift_") }?.let { name -> GiftKind.entries.firstOrNull { it.name == name } }?.let(::giftTopic)

    /** One synthetic topic per gift kind the player carries. */
    fun giftTopic(kind: GiftKind) = DialogueTopic(
        "gift_${kind.name}", TopicKind.FRIENDLY, "${kind.label} schenken", "Du reichst {npc} ${kind.label}.",
        special = "gift", successLines = listOf(""),
    )
}
