package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

@Serializable
enum class ChildTrait(val label: String) {
    CURIOUS("Neugierig"), BRAVE("Mutig"), CALM("Ruhig"), AMBITIOUS("Ehrgeizig"),
    COMPASSIONATE("Mitfühlend"), STUBBORN("Willensstark"), PATIENT("Geduldig"),
}

@Serializable
enum class EducationPath(val label: String, val description: String) {
    MILITARY("Militär", "Führung und Taktik; Lernen im sicheren Übungshof."),
    DIPLOMACY("Diplomatie", "Verhandeln, Zuhören und Konflikte vermitteln."),
    ADMINISTRATION("Verwaltung", "Versorgung planen und Verantwortung für Menschen tragen."),
    MEDICINE("Gelehrsamkeit / Medizin", "Lesen, Heilkunde und sorgfältige Beobachtung."),
}

@Serializable
data class FamilyDevelopmentEntry(val day: Int, val text: String)

@Serializable
data class FamilyEducationChoice(
    val id: String,
    val label: String,
    val consequence: String,
    val diplomacy: Int = 0,
    val leadership: Int = 0,
    val stewardship: Int = 0,
    val medicine: Int = 0,
    val tactics: Int = 0,
    val trait: ChildTrait? = null,
    val goldCost: Int = 0,
    val legitimacy: Int = 0,
    val mentorBond: Int = 0,
)

@Serializable
data class FamilyEducationEvent(
    val id: String, val memberId: String, val title: String, val text: String,
    val openedDay: Int, val choices: List<FamilyEducationChoice>,
)

@Serializable
data class FamilyEventOutcome(val day: Int, val eventId: String, val memberId: String, val text: String)

@Serializable
data class LegitimacyCause(val day: Int, val cause: String, val change: Int)

/** Stable content identifiers and age gates keep saved choices valid across updates. */
data class FamilyEventTemplate(
    val id: String, val minAge: Int, val maxAge: Int,
    val title: String, val text: String, val choices: List<FamilyEducationChoice>,
)

object FamilyEducationCatalog {
    val events: List<FamilyEventTemplate> = listOf(
        FamilyEventTemplate("first_questions", 3, 8, "Warum leuchtet die Mauer?",
            "{child} fragt beim Abendessen, warum Menschen und Elben dieselbe Grenze schützen.", listOf(
                FamilyEducationChoice("story", "Eine Geschichte beider Völker erzählen", "Neugier und Verständnis wachsen.", diplomacy = 3, trait = ChildTrait.CURIOUS),
                FamilyEducationChoice("walk", "Gemeinsam den sicheren Wachturm besuchen", "Das Kind gewinnt Mut und lernt Verantwortung.", leadership = 3, trait = ChildTrait.BRAVE),
                FamilyEducationChoice("listen", "Erst zuhören, was das Kind selbst denkt", "Ruhe und Selbstvertrauen geben den eigenen Gedanken Platz.", diplomacy = 2, leadership = 1, trait = ChildTrait.CALM),
            )),
        FamilyEventTemplate("injured_bird", 4, 10, "Ein verletzter Vogel",
            "{child} findet im Palastgarten einen verletzten Vogel und bittet um Hilfe.", listOf(
                FamilyEducationChoice("healer", "Beim Hofheiler gemeinsam helfen", "Fürsorge wird zur ersten Erfahrung in Heilkunde.", medicine = 4, trait = ChildTrait.COMPASSIONATE),
                FamilyEducationChoice("observe", "Ein ruhiges Nest bauen und beobachten", "Geduld und sorgfältige Beobachtung werden geübt.", medicine = 2, stewardship = 2, trait = ChildTrait.PATIENT),
                FamilyEducationChoice("delegate", "Die Tierpfleger holen und Verantwortung teilen", "Das Kind lernt, Hilfe rechtzeitig zu organisieren.", leadership = 2, diplomacy = 2, trait = ChildTrait.CALM),
            )),
        FamilyEventTemplate("siblings_dispute", 6, 17, "Der Streit im Lernzimmer",
            "{child} und ein anderes Kind beanspruchen dasselbe Buch. Beide fühlen sich übergangen.", listOf(
                FamilyEducationChoice("mediate", "Beide erklären lassen und vermitteln", "Verhandeln und Rücksicht werden geübt.", diplomacy = 4, trait = ChildTrait.COMPASSIONATE),
                FamilyEducationChoice("schedule", "Gemeinsam einen fairen Leseplan machen", "Eine klare Abmachung beruhigt den Streit.", stewardship = 4, trait = ChildTrait.PATIENT),
                FamilyEducationChoice("solve", "Die Kinder eine eigene Lösung finden lassen", "Eigenständigkeit stärkt Führung und Willenskraft.", leadership = 3, diplomacy = 1, trait = ChildTrait.STUBBORN),
            )),
        FamilyEventTemplate("market_lesson", 6, 14, "Ein Tag auf dem Markt",
            "{child} entdeckt, wie Getreidepreise die Familien der Stadt verändern.", listOf(
                FamilyEducationChoice("accounts", "Mit den Händlern die Rechnung verstehen", "Die Versorgung wird als Aufgabe der Verwaltung verstanden.", stewardship = 4, trait = ChildTrait.CURIOUS),
                FamilyEducationChoice("families", "Mit den betroffenen Familien sprechen", "Zuhören stärkt diplomatisches Gespür.", diplomacy = 4, trait = ChildTrait.COMPASSIONATE),
                FamilyEducationChoice("fund", "Eine kleine Hilfskasse gemeinsam planen · 40 Gold", "Gezielte Hilfe zeigt Verantwortung und stärkt Anerkennung.", stewardship = 2, leadership = 2, goldCost = 40, legitimacy = 2),
            )),
        FamilyEventTemplate("training_choice", 8, 17, "Mut im Übungshof",
            "{child} möchte im geschützten Übungshof lernen, für andere einzustehen. Es geht um Übung, nicht um einen Einsatz.", listOf(
                FamilyEducationChoice("formation", "Gemeinsam eine Übungsformation leiten", "Führung entsteht durch klare, sichere Anweisungen.", leadership = 3, tactics = 2, trait = ChildTrait.BRAVE),
                FamilyEducationChoice("strategy", "Die Aufgabe auf dem Spielbrett lösen", "Bedachtes Planen stärkt Taktik und Geduld.", tactics = 4, trait = ChildTrait.CALM),
                FamilyEducationChoice("rescue", "Eine sichere Rettungsübung machen", "Schutz und Fürsorge geben dem Mut eine Aufgabe.", leadership = 2, medicine = 3, trait = ChildTrait.COMPASSIONATE),
            )),
        FamilyEventTemplate("mentor_difference", 10, 17, "Andere Fragen als der Mentor",
            "{child} widerspricht im Unterricht dem Mentor. Der Wunsch nach einem eigenen Weg ist ernst gemeint.", listOf(
                FamilyEducationChoice("debate", "Eine begründete Debatte ermöglichen", "Respektvoller Widerspruch stärkt Diplomatie und Bindung.", diplomacy = 4, mentorBond = 4, trait = ChildTrait.STUBBORN),
                FamilyEducationChoice("project", "Ein eigenes kleines Projekt vereinbaren", "Eigenständiges Arbeiten stärkt Verwaltung und Ehrgeiz.", stewardship = 4, trait = ChildTrait.AMBITIOUS),
                FamilyEducationChoice("together", "Kind und Mentor gemeinsam zuhören", "Ein neuer Lernplan entsteht aus gegenseitigem Verständnis.", leadership = 2, diplomacy = 2, mentorBond = 6, trait = ChildTrait.CALM),
            )),
        FamilyEventTemplate("hospital_visit", 10, 17, "Die Stimmen im Lazarett",
            "Bei einem begleiteten Besuch hört {child} den Verwundeten zu und fragt, was Regierung für sie tun kann.", listOf(
                FamilyEducationChoice("care", "Eine sichere Pflegeaufgabe begleiten", "Heilkunde verbindet Wissen mit Fürsorge.", medicine = 4, trait = ChildTrait.COMPASSIONATE),
                FamilyEducationChoice("logistics", "Verbände und Vorräte gemeinsam zählen", "Die Bedeutung verlässlicher Versorgung wird sichtbar.", stewardship = 4, medicine = 1),
                FamilyEducationChoice("letters", "Briefe an die Familien schreiben helfen", "Die Sorgen der Menschen werden diplomatisch verstanden.", diplomacy = 4, trait = ChildTrait.PATIENT),
            )),
        FamilyEventTemplate("envoy_language", 12, 17, "Worte eines fremden Hofes",
            "Ein Gesandter lobt {child}s Neugier, missversteht aber eine Frage als Forderung.", listOf(
                FamilyEducationChoice("clarify", "Die Frage freundlich gemeinsam erklären", "Diplomatie wächst an einem konkreten Missverständnis.", diplomacy = 4, legitimacy = 1),
                FamilyEducationChoice("research", "Die Sprache und Geschichte studieren", "Sorgfältiges Lernen erweitert Wissen und Geduld.", diplomacy = 2, medicine = 2, trait = ChildTrait.CURIOUS),
                FamilyEducationChoice("host", "Das Kind einen kleinen Empfang planen lassen", "Verantwortung macht Selbstvertrauen und Verwaltung sichtbar.", leadership = 3, stewardship = 2, trait = ChildTrait.AMBITIOUS),
            )),
        FamilyEventTemplate("heir_responsibility", 14, 17, "Die Last der Nachfolge",
            "{child} fragt, ob das eigene Leben nur aus Pflichten bestehen wird. Der Hof erwartet bereits viel.", listOf(
                FamilyEducationChoice("boundaries", "Pflichten und eigene Freiräume vereinbaren", "Ein verlässlicher Rahmen stärkt ruhige Führung.", leadership = 4, trait = ChildTrait.CALM, legitimacy = 2),
                FamilyEducationChoice("council", "Den Rat beim nächsten Gespräch begleiten", "Politische Verantwortung wird Schritt für Schritt gelernt.", diplomacy = 3, stewardship = 2, legitimacy = 1),
                FamilyEducationChoice("listen", "Über die persönlichen Hoffnungen sprechen", "Der eigene Weg darf mit Fürsorge verbunden bleiben.", diplomacy = 2, leadership = 2, trait = ChildTrait.COMPASSIONATE),
            )),
        FamilyEventTemplate("final_project", 16, 17, "Das letzte Ausbildungsprojekt",
            "{child} möchte vor dem Eintritt in den erwachsenen Hof eine Aufgabe eigenständig abschließen.", listOf(
                FamilyEducationChoice("supplies", "Eine Vorratsprüfung leiten", "Das Projekt stärkt verlässliche Verwaltung und Führung.", stewardship = 4, leadership = 2),
                FamilyEducationChoice("mediation", "Eine begleitete Vermittlung vorbereiten", "Diplomatische Fähigkeiten werden in der Praxis gefestigt.", diplomacy = 4, leadership = 2),
                FamilyEducationChoice("study", "Eine Studie für den Hofheiler schreiben", "Wissen und genaue Beobachtung vertiefen die Heilkunde.", medicine = 4, stewardship = 2, trait = ChildTrait.CURIOUS),
            )),
    )
}
