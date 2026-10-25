package com.goldenunicorn.troopmanager.model

/** Static content for acts, bosses, rival kings and default faces. Nothing here is persisted. */
object ActCatalog {

    // ---------------------------------------------------------------- rivals

    enum class Personality(val label: String, val description: String) {
        PROUD("Stolz", "Verlangt Respekt, fordert Zweikämpfe und Tribut."),
        GREEDY("Gierig", "Denkt in Gold. Tribut, Handel und Bestechung."),
        SUSPICIOUS("Misstrauisch", "Bricht Bündnisse, wenn du zu mächtig wirst."),
        ARROGANT("Hochmütig", "Hält dich für einen Emporkömmling, bis du sie beeindruckst."),
        AMBITIOUS("Ehrgeizig", "Will selbst Kaiser werden und greift Schwäche an."),
    }

    data class RivalDef(
        val id: String,
        val name: String,
        val title: String,
        val land: CrownLand,
        val capitalId: String,
        val personality: Personality,
        val startAttitude: Int,
        val portraitKey: String,
        val look: PortraitSpec,
        val motto: String,
    )

    val rivals: List<RivalDef> = listOf(
        RivalDef("gerold", "Gerold von Aldmark", "König von Aldmark", CrownLand.ALDMARK, "al_aldenburg", Personality.PROUD, -5, "knight",
            PortraitSpec(skin = 1, hair = 1, hairColor = 6, eyes = 1, beard = 2, armor = 0, armorColor = 3, marking = 0, background = 3),
            "„Aldmark beugt sich keinem Grenzherrn.“"),
        RivalDef("ylvana", "Ylvana vom Silberblatt", "Fürstin des Waldhains", CrownLand.WALDHAIN, "wh_wipfelruh", Personality.SUSPICIOUS, 0, "wood_elf",
            PortraitSpec(skin = 0, hair = 2, hairColor = 3, eyes = 2, elfEars = true, armor = 1, armorColor = 1, marking = 3, background = 1),
            "„Der Wald erinnert sich an jeden Verrat.“"),
        RivalDef("aerelith", "Aerelith die Strahlende", "Königin von Sonnenhain", CrownLand.SONNENHAIN, "sh_sonnenhof", Personality.ARROGANT, -10, "gold_elf",
            PortraitSpec(skin = 0, hair = 1, hairColor = 5, eyes = 4, elfEars = true, armor = 3, armorColor = 2, marking = 0, background = 2),
            "„Sterbliche Kronen vergehen wie Blüten.“"),
        RivalDef("corvin", "Corvin Velmar", "Handelsprinz der Küste", CrownLand.KUESTENREICH, "ks_saltmere", Personality.GREEDY, 10, "knight",
            PortraitSpec(skin = 2, hair = 0, hairColor = 1, eyes = 0, beard = 3, armor = 2, armorColor = 4, marking = 0, background = 4),
            "„Alles hat seinen Preis. Auch Frieden.“"),
        RivalDef("zhao", "Zhao Feng", "Generalissimus des Kranichwalls", CrownLand.KRANICHWALL, "kw_kranichtor", Personality.AMBITIOUS, -5, "wall_guard",
            PortraitSpec(skin = 3, hair = 3, hairColor = 0, eyes = 1, beard = 1, armor = 0, armorColor = 0, marking = 1, background = 0),
            "„Der Wall braucht einen Kaiser. Warum nicht mich?“"),
    )

    fun rival(id: String): RivalDef? = rivals.firstOrNull { it.id == id }
    fun rivalForLand(land: CrownLand): RivalDef? = rivals.firstOrNull { it.land == land }

    // ---------------------------------------------------------------- acts

    data class BossDef(
        val name: String,
        val title: String,
        val enemy: EnemyType,
        val strength: Int,
        val portraitKey: String,
        val intro: String,
        val location: String,
    )

    data class ActDef(
        val number: Int,
        val title: String,
        val summary: String,
        val boss: BossDef,
        val rewardGold: Int,
        val rewardRenown: Int,
    )

    val acts: List<ActDef> = listOf(
        ActDef(1, "Die Grenzmark",
            "Sichere vier Provinzen der Grenzmark, stelle ein Heer von 1.500 Mann auf und gewinne eine Feldherrin.",
            BossDef("Gor-Mukar", "Kriegshäuptling der Orks", EnemyType.ORC, 2_500, "orc",
                "Trommeln dröhnen aus den Wäldern. Gor-Mukar, der Kriegshäuptling der Orks, hat die Stämme vereint und will deine junge Herrschaft im Keim ersticken. Seine Horde steht vor der Grenzmark.",
                "Grenzmark"),
            5_000, 300),
        ActDef(2, "Die Krone",
            "Werde König, beherrsche 18 Kronprovinzen, führe 12.000 Soldaten und unterwirf oder verbünde dich mit zwei Rivalen-Königen.",
            BossDef("Skarnag", "Kriegsfürst der Uruk-hai", EnemyType.URUK, 12_000, "uruk",
                "Die Könige der Kronlande knien – da bricht Skarnag mit den Uruk-hai aus dem Hordenland hervor. Schwer gepanzert, unermüdlich, zahllos. Wenn dein Reich jetzt fällt, war alles umsonst.",
                "Aldmark"),
            15_000, 800),
        ActDef(3, "Der große Krieg",
            "Beherrsche 30 Kronprovinzen, erobere drei Provinzen im Hordenland, führe 40.000 Soldaten und zwinge vier Könige auf deine Seite.",
            BossDef("Die Tao-Tei-Königin", "Herz des Schwarms", EnemyType.TAO_TEI, 45_000, "monster",
                "Aus dem Krater steigt ein Kreischen, das kein Ende nimmt. Der Tao-Tei-Schwarm erwacht – und an seiner Spitze die Königin. Hält dein Reich diesem Ansturm stand, gehört die Welt dir.",
                "Hordenland"),
            40_000, 2_000),
    )

    fun act(number: Int): ActDef? = acts.firstOrNull { it.number == number }

    // ---------------------------------------------------------------- faces

    fun heroineLook(id: String): PortraitSpec = when (id) {
        "alina" -> PortraitSpec(skin = 1, hair = 2, hairColor = 4, eyes = 2, elfEars = true, armor = 1, armorColor = 1, marking = 0, background = 1)
        "seraphine" -> PortraitSpec(skin = 1, hair = 0, hairColor = 0, eyes = 3, armor = 0, armorColor = 5, marking = 1, background = 3)
        "liang" -> PortraitSpec(skin = 3, hair = 3, hairColor = 0, eyes = 1, armor = 0, armorColor = 0, marking = 0, background = 0)
        "ithiliel" -> PortraitSpec(skin = 0, hair = 1, hairColor = 5, eyes = 4, elfEars = true, armor = 3, armorColor = 2, marking = 3, background = 2)
        else -> PortraitSpec()
    }

    fun defaultPlayerLook(species: Species): PortraitSpec =
        PortraitSpec(skin = 1, hair = 0, hairColor = 1, eyes = 0, elfEars = species != Species.HUMAN, beard = 0, armor = 0, armorColor = 3, background = 3)
}
