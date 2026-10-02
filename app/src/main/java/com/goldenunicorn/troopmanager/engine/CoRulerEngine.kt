package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/** Decisions spend real campaign resources. Delegation is opt-in and never starts a war. */
object CoRulerEngine {
    fun validate(state: GameState) {
        val policy = state.coRuler.delegation
        require(policy.maxGoldPerAction in 0..500 && policy.maxGoldPerDay in 0..500 && policy.maxGoldPerAction <= policy.maxGoldPerDay) { "Ungültige Delegationsgrenze." }
        require(state.coRuler.goldSpentToday >= 0 && state.coRuler.regencyEfficiency in 0..100) { "Ungültiger Regierungsbericht." }
        require(state.coRuler.decisions.all { it.goldSpent >= 0 && it.efficiency in 0..100 && it.day >= 1 }) { "Ungültige Ratsentscheidung." }
    }

    private fun gold(amount: Int) = Resources(amount, 0, 0, 0, 0)
    private fun option(id: String, label: String, explanation: String, cost: Resources = gold(0),
        reward: Resources = gold(0), effects: CouncilEffects = CouncilEffects(), action: DelegatedAction? = null) =
        CouncilOption(id, label, explanation, cost, reward, effects, action)

    fun isCoRuler(state: GameState): Boolean = state.companion.met && state.relationship.romanceStage == RomanceStage.CO_RULERS

    /** 12 governing dilemmas and 12 concrete conflicts; conditions are evaluated against the current realm. */
    fun catalog(state: GameState): List<CouncilCase> {
        fun entry(id: String, portfolio: CoRulerPortfolio, title: String, situation: String,
            vararg options: CouncilOption, court: Boolean = false) = CouncilCase(id,
                if (court) CouncilCaseKind.COURT_CONFLICT else CouncilCaseKind.GOVERNMENT,
                portfolio, title, situation, options.toList())
        return listOf(
            entry("grain", CoRulerPortfolio.ECONOMY, "Vorräte für die nächste Woche",
                "${state.resources.food} Nahrung; Hunger ${state.society.hunger}. Händler bieten eine begrenzte Lieferung an.",
                option("buy", "400 Nahrung einkaufen", "200 Gold; der Vorrat wächst um höchstens 400 Nahrung.", gold(200), Resources(0,400,0,0,0), action = DelegatedAction.SUPPLY_PURCHASE),
                option("farm", "Arbeitskräfte zur Nahrung", "Nahrungsproduktion priorisieren; Zufriedenheit −1.", effects = CouncilEffects(priority=WorkerPriority.FOOD,satisfaction=-1)),
                option("reserve", "Vorräte halten", "Keine Ausgabe; ein öffentlicher Vorratsplan stärkt Sicherheit um 1.", effects=CouncilEffects(security=1))),
            entry("refugees", CoRulerPortfolio.INTERIOR, "Unterkünfte für Neuankömmlinge",
                "${state.society.lastMigration} neue Bewohner; kulturelle Spannung ${state.society.culturalTension}.",
                option("welcome", "Gemeinsame Küchen öffnen", "150 Gold, 120 Nahrung; Zufriedenheit +4, Kulturspannung −4.", Resources(150,120,0,0,0), effects=CouncilEffects(satisfaction=4,culturalTension=-4),action=DelegatedAction.HUMANITARIAN_AID),
                option("register", "Ankunft geordnet erfassen", "80 Gold; Sicherheit +3, Kulturspannung −1.", gold(80),effects=CouncilEffects(security=3,culturalTension=-1),action=DelegatedAction.CIVIL_ADMINISTRATION),
                option("local", "Viertel um Hilfe bitten", "Zufriedenheit +1; Wohlstand −1 durch lokale Kosten.",effects=CouncilEffects(satisfaction=1,prosperity=-1))),
            entry("hospital", CoRulerPortfolio.INTERIOR, "Platz für die Verwundeten",
                "${state.war.wounded.sumOf { it.soldiers }} Soldaten warten auf Genesung.",
                option("healers", "Zusätzliche Heiler bezahlen", "180 Gold; bestehende Genesungsfristen einen Tag früher, frühestens morgen.", gold(180),effects=CouncilEffects(healingDays=1,satisfaction=2),action=DelegatedAction.HUMANITARIAN_AID),
                option("rest", "Versorgung aus der Garnison", "180 Nahrung; Moral +2, Genesung einen Tag früher.", Resources(0,180,0,0,0),effects=CouncilEffects(healingDays=1,morale=2)),
                option("capacity", "Bestehende Pflege schützen", "Keine Ausgabe; Kriegserschöpfung −1, Genesungsfristen bleiben bestehen.",effects=CouncilEffects(warExhaustion=-1))),
            entry("tax", CoRulerPortfolio.ECONOMY, "Steuern vor dem nächsten Erntetag",
                "${state.city.taxLevel.label}e Steuern; Zufriedenheit ${state.city.satisfaction}, Gold ${state.resources.gold}.",
                option("relief", "Steuern senken", "Niedrige Steuern gelten ab heute; Zufriedenheit +3.",effects=CouncilEffects(taxLevel=TaxLevel.LOW,satisfaction=3)),
                option("normal", "Reguläre Abgaben", "Normale Steuern und nachvollziehbare Abrechnung; Sicherheit +1.",effects=CouncilEffects(taxLevel=TaxLevel.NORMAL,security=1),action=DelegatedAction.CIVIL_ADMINISTRATION),
                option("high", "Verteidigung finanzieren", "Hohe Steuern; Zufriedenheit −3, Sicherheit +2.",effects=CouncilEffects(taxLevel=TaxLevel.HIGH,satisfaction=-3,security=2))),
            entry("occupation", CoRulerPortfolio.INTERIOR, "Integration statt weiterer Unruhe",
                "${state.occupations.size} besetzte Gebiete; höchste Unruhe ${state.occupations.maxOfOrNull { it.unrest } ?: 0}.",
                option("listen", "Örtliche Vertreter anhören", "120 Gold; Besatzungsunruhe −6, kulturelle Spannung −3.",gold(120),effects=CouncilEffects(occupationUnrest=-6,culturalTension=-3),action=DelegatedAction.CIVIL_ADMINISTRATION),
                option("schools", "Gemeinsame Verwaltung schulen", "220 Gold; Unruhe −8, Wohlstand +2.",gold(220),effects=CouncilEffects(occupationUnrest=-8,prosperity=2),action=DelegatedAction.CIVIL_ADMINISTRATION),
                option("garrison", "Garnison öffentlich erklären", "Sicherheit +2; Unruhe −2, Kulturspannung +1.",effects=CouncilEffects(security=2,occupationUnrest=-2,culturalTension=1))),
            entry("trade", CoRulerPortfolio.DIPLOMACY, "Ein Handelskontakt ohne Bündniszwang",
                "Ein friedlicher Nachbar kann einen Gesandten empfangen; keine militärische Verpflichtung.",
                option("envoy", "Gesandten und Warenprobe senden", "160 Gold; Beziehungen zum erreichbaren friedlichen Nachbarn +5.",gold(160),effects=CouncilEffects(diplomaticRelation=5),action=DelegatedAction.DIPLOMATIC_CONTACT),
                option("exchange", "Kleine Versorgung tauschen", "100 Gold und 80 Nahrung; Beziehungen +7, Wohlstand +2.",Resources(100,80,0,0,0),effects=CouncilEffects(diplomaticRelation=7,prosperity=2),action=DelegatedAction.DIPLOMATIC_CONTACT),
                option("internal", "Zuerst den eigenen Markt stärken", "80 Gold; Wohlstand +3, Handelsproduktion priorisieren.",gold(80),effects=CouncilEffects(prosperity=3,priority=WorkerPriority.TRADE))),
            entry("wall", CoRulerPortfolio.MILITARY, "Die Mauer vor dem nächsten Angriff",
                "Mauerintegrität ${state.realm.wallIntegrity}; Grenzbedrohung ${state.realm.threat}.",
                option("repair", "Lücken schließen", "140 Gold, 80 Stein; Mauerintegrität +8, höchstens 100.",Resources(140,0,0,80,0),effects=CouncilEffects(wallRepair=8),action=DelegatedAction.DEFENSIVE_REPAIR),
                option("watch", "Zusätzliche Nachtwache", "80 Gold und 80 Nahrung; Sicherheit +4, Moral +1.",Resources(80,80,0,0,0),effects=CouncilEffects(security=4,morale=1),action=DelegatedAction.DEFENSIVE_REPAIR),
                option("stone", "Steinproduktion vorziehen", "Stein hat Produktionsvorrang; Wohlstand −1.",effects=CouncilEffects(priority=WorkerPriority.STONE,prosperity=-1))),
            entry("field_supply", CoRulerPortfolio.MILITARY, "Nachschub für das Feldheer",
                "${state.world.playerFieldArmies.size} Feldheere; knappster Vorrat ${state.world.playerFieldArmies.minOfOrNull { it.supplyDays } ?: 0} Tage.",
                option("convoy", "200 Nahrung ins knappste Heer", "40 Gold und 200 Nahrung werden real ins Heer überführt; Moral +2.",Resources(40,200,0,0,0),effects=CouncilEffects(fieldSupply=200,morale=2),action=DelegatedAction.HUMANITARIAN_AID),
                option("large", "400 Nahrung bereitstellen", "80 Gold und 400 Nahrung für das knappste Heer.",Resources(80,400,0,0,0),effects=CouncilEffects(fieldSupply=400)),
                option("harvest", "Heimatversorgung priorisieren", "Arbeitspriorität Nahrung; keine Feldvorräte entstehen sofort.",effects=CouncilEffects(priority=WorkerPriority.FOOD))),
            entry("training", CoRulerPortfolio.MILITARY, "Übung oder Ruhe für die Garnison",
                "${state.homeArmySize} Soldaten zu Hause; Kriegserschöpfung ${state.society.warExhaustion}.",
                option("drill", "Disziplinierter Übungstag", "90 Gold und 100 Nahrung; Moral +3, Sicherheit +2.",Resources(90,100,0,0,0),effects=CouncilEffects(morale=3,security=2)),
                option("rest", "Erholung organisieren", "60 Gold; Kriegserschöpfung −3, Moral +2.",gold(60),effects=CouncilEffects(warExhaustion=-3,morale=2),action=DelegatedAction.HUMANITARIAN_AID),
                option("officers", "Ausbilder beraten lassen", "Sicherheit +1, Loyalität der Kommandanten +1.",effects=CouncilEffects(security=1,loyalty=1))),
            entry("production", CoRulerPortfolio.ECONOMY, "Die Werkstätten verlangen Klarheit",
                "${state.trainingSize} Soldaten in Ausbildung; ${state.city.workerPriority.label} hat derzeit Vorrang.",
                option("iron", "Arsenal vorziehen", "Eisenproduktion priorisieren; Sicherheit +2, Zufriedenheit −1.",effects=CouncilEffects(priority=WorkerPriority.IRON,security=2,satisfaction=-1)),
                option("balance", "Versorgung ausgewogen halten", "Ausgewogene Produktion; Zufriedenheit +1.",effects=CouncilEffects(priority=WorkerPriority.BALANCED,satisfaction=1),action=DelegatedAction.CIVIL_ADMINISTRATION),
                option("market", "Handelsaufträge bündeln", "70 Gold; Handelsproduktion priorisieren, Wohlstand +3.",gold(70),effects=CouncilEffects(priority=WorkerPriority.TRADE,prosperity=3))),
            entry("delegates", CoRulerPortfolio.COURT, "Eine öffentliche Audienz",
                "${state.court.offices.size} Hofämter besetzt; politische Loyalität ${state.society.politicalLoyalty}.",
                option("public", "Bürger und Hof gemeinsam anhören", "110 Gold; Zufriedenheit +3, Loyalität +2.",gold(110),effects=CouncilEffects(satisfaction=3,loyalty=2),action=DelegatedAction.COURT_MEDIATION),
                option("small", "Kleine sachliche Sitzung", "50 Gold; Sicherheit +2, Loyalität +1.",gold(50),effects=CouncilEffects(security=2,loyalty=1),action=DelegatedAction.COURT_MEDIATION),
                option("letters", "Schriftliche Anliegen bearbeiten", "Wohlstand +1; jeder Amtsträger bekommt eine Antwort.",effects=CouncilEffects(prosperity=1))),
            entry("exhaustion", CoRulerPortfolio.INTERIOR, "Das Reich braucht einen ruhigeren Tag",
                "Kriegserschöpfung ${state.society.warExhaustion}; Zufriedenheit ${state.city.satisfaction}.",
                option("relief", "Hinterbliebene und Veteranen versorgen", "200 Gold und 150 Nahrung; Kriegserschöpfung −5, Zufriedenheit +3.",Resources(200,150,0,0,0),effects=CouncilEffects(warExhaustion=-5,satisfaction=3),action=DelegatedAction.HUMANITARIAN_AID),
                option("memorial", "Würdiges Gedenken", "90 Gold; Kriegserschöpfung −3, Moral +2.",gold(90),effects=CouncilEffects(warExhaustion=-3,morale=2),action=DelegatedAction.COURT_MEDIATION),
                option("facts", "Die Lage offen erklären", "Kriegserschöpfung −1, Sicherheit +1.",effects=CouncilEffects(warExhaustion=-1,security=1))),
            entry("promotion_dispute", CoRulerPortfolio.COURT, "Zwei Anwärter, ein Kommando",
                "Ehrgeiz und bisherige Siege konkurrieren; Rang wird weiter durch das Beförderungssystem verliehen.",
                option("merit", "Leistung öffentlich würdigen", "90 Gold; Loyalität +3, Moral +1, keine unverdiente Beförderung.",gold(90),effects=CouncilEffects(loyalty=3,morale=1),action=DelegatedAction.COURT_MEDIATION),
                option("rotate", "Verantwortung zeitlich teilen", "Sicherheit +2, Loyalität +1; keine Armee wird entsandt.",effects=CouncilEffects(security=2,loyalty=1)),
                option("review", "Ein Amtsträger prüft beide Karrieren", "40 Gold; Loyalität +2.",gold(40),effects=CouncilEffects(loyalty=2),action=DelegatedAction.COURT_MEDIATION),court=true),
            entry("office_budget", CoRulerPortfolio.COURT, "Heiler und Schatzmeister streiten",
                "Heilkunde braucht Mittel, die Kasse braucht Reserven; ${state.resources.gold} Gold verfügbar.",
                option("health", "Heilkunde erhält Vorrang", "130 Gold; Genesung einen Tag früher, Zufriedenheit +2.",gold(130),effects=CouncilEffects(healingDays=1,satisfaction=2),action=DelegatedAction.HUMANITARIAN_AID),
                option("audit", "Ausgaben gemeinsam prüfen", "60 Gold; Wohlstand +2, Loyalität +1.",gold(60),effects=CouncilEffects(prosperity=2,loyalty=1),action=DelegatedAction.COURT_MEDIATION),
                option("reserve", "Kassenreserve schützen", "Wohlstand +1, Loyalität −1.",effects=CouncilEffects(prosperity=1,loyalty=-1)),court=true),
            entry("recognition", CoRulerPortfolio.COURT, "Der Hof zweifelt an der Mitregentin",
                "Ihre Amtsstimme verlangt Anerkennung; Respekt ${state.companion.respect}.",
                option("charter", "Ressort und Stimme öffentlich bestätigen", "100 Gold; Respekt +3, Loyalität +2.",gold(100),effects=CouncilEffects(companionRespect=3,loyalty=2),action=DelegatedAction.COURT_MEDIATION),
                option("achievement", "Ihre konkrete Arbeit vorstellen", "50 Gold; Respekt +2, Wohlstand +1.",gold(50),effects=CouncilEffects(companionRespect=2,prosperity=1),action=DelegatedAction.COURT_MEDIATION),
                option("hear", "Einwände sachlich anhören", "Respekt +1, Sicherheit +1; ihre Stimme bleibt erhalten.",effects=CouncilEffects(companionRespect=1,security=1)),court=true),
            entry("rival_orders", CoRulerPortfolio.MILITARY, "Rivalen geben widersprüchliche Befehle",
                "${state.court.socialLinks.count { it.kind == SocialKind.RIVALRY }} Rivalitätsverbindungen belasten die Führung.",
                option("mediation", "Gemeinsame Befehlskette vereinbaren", "80 Gold; Sicherheit +3, Loyalität +2.",gold(80),effects=CouncilEffects(security=3,loyalty=2),action=DelegatedAction.COURT_MEDIATION),
                option("discipline", "Verantwortung schriftlich festlegen", "Sicherheit +4, Loyalität −1.",effects=CouncilEffects(security=4,loyalty=-1)),
                option("exchange", "Erfahrungen vor der Garnison austauschen", "70 Gold; Moral +3, Loyalität +1.",gold(70),effects=CouncilEffects(morale=3,loyalty=1)),court=true),
            entry("envoy_insult", CoRulerPortfolio.DIPLOMACY, "Ein Gesandter spricht herablassend",
                "Ein verletzter Stolz braucht eine Antwort, ohne einen Krieg zu provozieren.",
                option("firm", "Sachlich auf gleichen Rang bestehen", "50 Gold; Respekt +2, Beziehungen +2.",gold(50),effects=CouncilEffects(companionRespect=2,diplomaticRelation=2),action=DelegatedAction.DIPLOMATIC_CONTACT),
                option("private", "Unter vier Augen vermitteln", "80 Gold; Beziehungen +4, Loyalität +1.",gold(80),effects=CouncilEffects(diplomaticRelation=4,loyalty=1),action=DelegatedAction.DIPLOMATIC_CONTACT),
                option("protocol", "Empfang mit klaren Regeln beenden", "Sicherheit +2, Respekt +1; keine Kriegserklärung.",effects=CouncilEffects(security=2,companionRespect=1)),court=true),
            entry("festival_budget", CoRulerPortfolio.COURT, "Fest oder Soldatenversorgung",
                "Der Hof plant ein Fest; ${state.resources.food} Nahrung und ${state.war.wounded.sumOf { it.soldiers }} Verwundete stehen dagegen.",
                option("modest", "Kleines Fest mit der Garnison", "90 Gold, 70 Nahrung; Zufriedenheit +3, Moral +2.",Resources(90,70,0,0,0),effects=CouncilEffects(satisfaction=3,morale=2),action=DelegatedAction.COURT_MEDIATION),
                option("patients", "Festmittel den Verwundeten geben", "120 Gold; Genesung einen Tag früher, Moral +2.",gold(120),effects=CouncilEffects(healingDays=1,morale=2),action=DelegatedAction.HUMANITARIAN_AID),
                option("postpone", "Fest mit Begründung vertagen", "Wohlstand +1; Loyalität −1.",effects=CouncilEffects(prosperity=1,loyalty=-1)),court=true),
            entry("merchant_guild", CoRulerPortfolio.ECONOMY, "Gilde und Verwalter blockieren einander",
                "Handelsprivilegien treffen auf Bürgerinteressen; Wohlstand ${state.city.prosperity}.",
                option("open", "Offenen Markt zusichern", "70 Gold; Wohlstand +3, Kulturspannung −1.",gold(70),effects=CouncilEffects(prosperity=3,culturalTension=-1),action=DelegatedAction.CIVIL_ADMINISTRATION),
                option("rules", "Gemeinsame Handelsregeln", "40 Gold; Sicherheit +2, Wohlstand +1.",gold(40),effects=CouncilEffects(security=2,prosperity=1),action=DelegatedAction.COURT_MEDIATION),
                option("citizens", "Bürgerstände zuerst anhören", "Zufriedenheit +2, Wohlstand −1.",effects=CouncilEffects(satisfaction=2,prosperity=-1)),court=true),
            entry("cultural_seating", CoRulerPortfolio.INTERIOR, "Wer sitzt bei der Audienz vorn?",
                "Die Kulturen empfinden die Sitzordnung als Wertung; Spannung ${state.society.culturalTension}.",
                option("equal", "Gleiche Vertretung vereinbaren", "60 Gold; Kulturspannung −4, Loyalität +1.",gold(60),effects=CouncilEffects(culturalTension=-4,loyalty=1),action=DelegatedAction.COURT_MEDIATION),
                option("rotate", "Den Vorsitz abwechseln", "Kulturspannung −2, Sicherheit +1.",effects=CouncilEffects(culturalTension=-2,security=1),action=DelegatedAction.CIVIL_ADMINISTRATION),
                option("task", "Sitzplätze nach Aufgabe ordnen", "Sicherheit +3, Kulturspannung −1.",effects=CouncilEffects(security=3,culturalTension=-1)),court=true),
            entry("loyalty_doubt", CoRulerPortfolio.COURT, "Eine Führungskraft fühlt sich übergangen",
                "Niedrigste Kommandantenloyalität ${state.commanders.minOfOrNull { it.loyalty } ?: 100}; Anerkennung fehlt.",
                option("hear", "Den Dienst persönlich würdigen", "70 Gold; Loyalität +4, Moral +1.",gold(70),effects=CouncilEffects(loyalty=4,morale=1),action=DelegatedAction.COURT_MEDIATION),
                option("duty", "Klare Verantwortung vereinbaren", "Sicherheit +2, Loyalität +2.",effects=CouncilEffects(security=2,loyalty=2)),
                option("witnesses", "Kameraden gemeinsam anhören", "30 Gold; Loyalität +3.",gold(30),effects=CouncilEffects(loyalty=3),action=DelegatedAction.COURT_MEDIATION),court=true),
            entry("rumor", CoRulerPortfolio.COURT, "Gerüchte über bevorzugte Höflinge",
                "Politische Loyalität ${state.society.politicalLoyalty}; der Hof verlangt nachvollziehbare Entscheidungen.",
                option("accounts", "Zuwendungen offenlegen", "60 Gold; Sicherheit +3, Loyalität +2.",gold(60),effects=CouncilEffects(security=3,loyalty=2),action=DelegatedAction.COURT_MEDIATION),
                option("dialogue", "Betroffene gemeinsam anhören", "50 Gold; Loyalität +3, Zufriedenheit +1.",gold(50),effects=CouncilEffects(loyalty=3,satisfaction=1),action=DelegatedAction.COURT_MEDIATION),
                option("rules", "Gleiche Vergaberegeln erlassen", "Sicherheit +2, Wohlstand +1.",effects=CouncilEffects(security=2,prosperity=1)),court=true),
            entry("frontier_priority", CoRulerPortfolio.MILITARY, "Grenzschutz und Heer konkurrieren",
                "Bedrohung ${state.realm.threat}; ${state.homeArmySize} Soldaten schützen die Heimat.",
                option("home", "Heimatwache versorgen", "80 Gold, 100 Nahrung; Sicherheit +4, Moral +2.",Resources(80,100,0,0,0),effects=CouncilEffects(security=4,morale=2),action=DelegatedAction.DEFENSIVE_REPAIR),
                option("scouts", "Bestehende Wachtberichte bündeln", "60 Gold; Sicherheit +3, Loyalität +1; keine Mission wird ohne Befehl gestartet.",gold(60),effects=CouncilEffects(security=3,loyalty=1),action=DelegatedAction.CIVIL_ADMINISTRATION),
                option("balance", "Zuständigkeiten gemeinsam begrenzen", "Sicherheit +2, Loyalität +2.",effects=CouncilEffects(security=2,loyalty=2)),court=true),
            entry("family_council", CoRulerPortfolio.COURT, "Familienpflicht und Audienz kollidieren",
                "${state.dynasty.members.count { it.alive }} Angehörige; Reichsaufgaben lassen wenig gemeinsame Zeit.",
                option("schedule", "Geschützte Zeit und Vertretung planen", "50 Gold; Respekt +2, Loyalität +1.",gold(50),effects=CouncilEffects(companionRespect=2,loyalty=1),action=DelegatedAction.COURT_MEDIATION),
                option("share", "Audienzen auf Amtsträger verteilen", "Sicherheit +2, Respekt +1.",effects=CouncilEffects(security=2,companionRespect=1)),
                option("explain", "Heutige Frist gemeinsam erklären", "Respekt +1; die Familie erhält eine verbindliche Begründung.",effects=CouncilEffects(companionRespect=1)),court=true),
        )
    }

    private fun eligible(state: GameState, case: CouncilCase): Boolean = when (case.id) {
        "hospital", "office_budget" -> state.war.wounded.isNotEmpty()
        "occupation" -> state.occupations.isNotEmpty()
        "trade", "envoy_insult" -> diplomaticTarget(state) != null
        "wall" -> state.realm.wallIntegrity < 100 || state.realm.threat >= 25
        "field_supply" -> state.world.playerFieldArmies.isNotEmpty()
        "recognition" -> isCoRuler(state)
        "promotion_dispute" -> state.commanders.count { it.id != COMPANION_COMMANDER_ID } >= 2
        "rival_orders" -> state.court.socialLinks.any { it.kind == SocialKind.RIVALRY } || state.court.characters.any { it.rivals.isNotEmpty() }
        "family_council" -> state.dynasty.members.any { it.alive }
        "loyalty_doubt" -> state.commanders.any { it.loyalty < 65 }
        "refugees" -> state.society.lastMigration > 0 || state.society.culturalTension > 15
        "exhaustion" -> state.society.warExhaustion > 0
        else -> true
    }

    private fun urgency(state: GameState, case: CouncilCase): Int = when (case.id) {
        "grain" -> if (state.resources.food < 1200 || state.society.hunger > 10) 100 else 10
        "hospital", "office_budget" -> 65 + state.war.wounded.sumOf { it.soldiers }.coerceAtMost(30)
        "wall", "frontier_priority" -> state.realm.threat + 100 - state.realm.wallIntegrity
        "field_supply" -> if ((state.world.playerFieldArmies.minOfOrNull { it.supplyDays } ?: 9) < 3) 100 else 35
        "occupation" -> state.occupations.maxOfOrNull { it.unrest } ?: 0
        "exhaustion" -> state.society.warExhaustion
        "loyalty_doubt" -> 100 - (state.commanders.minOfOrNull { it.loyalty } ?: 100)
        "cultural_seating", "refugees" -> state.society.culturalTension
        "tax" -> 100 - state.city.satisfaction
        "recognition" -> 100 - state.companion.respect
        else -> 15
    }

    private fun candidates(state: GameState) = catalog(state).filter { eligible(state,it) &&
        state.day - (state.coRuler.resolvedDays[it.id] ?: -100) >= 7 }

    private fun selectedCases(state: GameState): List<CouncilCase> {
        val pending = candidates(state).filter { it.id in state.coRuler.pendingCaseIds }
        if (pending.isNotEmpty() || state.coRuler.agendaDay == state.day) return pending
        val available = candidates(state)
        val rotating = available.sortedWith(compareByDescending<CouncilCase> { urgency(state,it) }
            .thenBy { (catalog(state).indexOfFirst { c -> c.id == it.id } - state.day / 3).mod(24) })
        val governing = rotating.firstOrNull { it.kind == CouncilCaseKind.GOVERNMENT }
        val conflict = rotating.firstOrNull { it.kind == CouncilCaseKind.COURT_CONFLICT }
        return (listOfNotNull(governing, conflict) + rotating).distinctBy { it.id }.take(4)
    }

    fun councilCases(state: GameState): List<CouncilCase> = selectedCases(state).map { it.copy(recommendations = recommendations(state,it)) }

    private fun diplomaticTarget(state: GameState) = state.world.factions.filter {
        it.id !in listOf(PLAYER_FACTION, NEUTRAL_FACTION) && !DiplomacyEngine.atWar(state, PLAYER_FACTION, it.id)
    }.maxByOrNull { DiplomacyEngine.relation(state, PLAYER_FACTION, it.id).relation }

    private fun recommendationScore(state: GameState, option: CouncilOption, role: CourtOffice? = null,
        personality: CharacterPersonality? = null, companion: Boolean = false): Int {
        val e = option.effects
        var score = e.satisfaction * (if (state.city.satisfaction < 45) 4 else 1) +
            e.security * (if (state.realm.threat > 35 || state.city.security < 45) 4 else 1) + e.prosperity + e.morale + e.loyalty * 2 -
            e.culturalTension * 2 - e.occupationUnrest - e.warExhaustion * 2 + e.wallRepair * (if (state.realm.wallIntegrity < 80) 3 else 1) +
            e.healingDays * (if (state.war.wounded.isNotEmpty()) 15 else 0) + e.diplomaticRelation * 2 +
            e.fieldSupply / (if ((state.world.playerFieldArmies.minOfOrNull { it.supplyDays } ?: 9) < 3) 20 else 100) +
            option.reward.food / (if (state.resources.food < 1200) 20 else 100)
        score -= option.cost.gold / (if (state.resources.gold < 1000) 15 else 80)
        when (role) {
            CourtOffice.MARSHAL -> score += e.morale * 3 + e.security * 2 + e.wallRepair
            CourtOffice.TREASURER -> score += e.prosperity * 3 - option.cost.gold / 25
            CourtOffice.PHYSICIAN -> score += e.healingDays * 30 - e.warExhaustion * 3
            CourtOffice.AMBASSADOR -> score += e.diplomaticRelation * 4 - e.culturalTension
            CourtOffice.STEWARD -> score += e.satisfaction * 3 - e.occupationUnrest * 2
            CourtOffice.SPYMASTER -> score += e.security * 4
            CourtOffice.MASTER_BUILDER -> score += e.wallRepair * 4
            null -> Unit
        }
        when (personality) {
            CharacterPersonality.COMPASSIONATE -> score += e.satisfaction * 3 + e.healingDays * 15 - e.warExhaustion * 2
            CharacterPersonality.MERCANTILE -> score += e.prosperity * 4 - option.cost.gold / 30
            CharacterPersonality.CAUTIOUS -> score += e.security * 3 - option.cost.gold / 50
            CharacterPersonality.AMBITIOUS -> score += e.morale * 3 + e.loyalty
            CharacterPersonality.DISCIPLINED -> score += e.security * 3 + e.morale * 2
            CharacterPersonality.HONORABLE -> score += e.loyalty * 3 - e.culturalTension
            null -> Unit
        }
        if (companion) {
            state.relationship.personality.priorities.forEachIndexed { i, p ->
                val weight = (5-i).coerceAtLeast(1)
                score += weight * when (p) {
                    CompanionPriority.WOUNDED -> e.healingDays * 8
                    CompanionPriority.SUPPLY -> option.reward.food / 40 + e.fieldSupply / 40 + if (e.priority == WorkerPriority.FOOD) 4 else 0
                    CompanionPriority.DEFENSE -> e.security * 2 + e.wallRepair
                    CompanionPriority.DIPLOMACY -> e.diplomaticRelation * 2
                    CompanionPriority.INTEGRATION -> -e.culturalTension - e.occupationUnrest
                    CompanionPriority.TRADE -> e.prosperity * 2
                    CompanionPriority.FAMILY -> -e.warExhaustion + e.companionRespect
                    CompanionPriority.RECOGNITION -> e.companionRespect * 3 + e.loyalty
                    CompanionPriority.EXPANSION -> e.security + e.prosperity
                }
            }
            if (CompanionTrait.CAUTIOUS in state.relationship.personality.traits) score += e.security * 2 - option.cost.gold / 60
            if (CompanionTrait.COMPASSIONATE in state.relationship.personality.traits) score += e.healingDays * 10 + e.satisfaction * 2
            if (CompanionTrait.DIPLOMATIC in state.relationship.personality.traits) score += e.diplomaticRelation * 3
            if (CompanionTrait.PROUD in state.relationship.personality.traits) score += e.companionRespect * 3
        }
        if (!affordable(state,option)) score -= 10000
        return score
    }

    fun recommendations(state: GameState, case: CouncilCase): List<CouncilRecommendation> {
        val list = mutableListOf<CouncilRecommendation>()
        val rulerOption = case.options.maxByOrNull { recommendationScore(state,it) } ?: return emptyList()
        list += CouncilRecommendation(state.player.name, "Herrscher", rulerOption.id,
            "Versorgung ${state.resources.food}, Gold ${state.resources.gold}, Sicherheit ${state.city.security}: ${rulerOption.explanation}")
        if (isCoRuler(state)) {
            val choice = case.options.maxBy { recommendationScore(state,it,companion=true) }
            list += CouncilRecommendation(state.companion.name, "Mitregentin · ${state.coRuler.portfolio.label}", choice.id,
                "Meine Prioritäten ${state.relationship.personality.priorities.take(3).joinToString { it.label }} und ${state.relationship.personality.traits.take(2).joinToString { it.label.lowercase() }}: ${choice.explanation}")
        }
        state.court.offices.entries.forEach { (office,id) ->
            if (id == COMPANION_COMMANDER_ID && isCoRuler(state)) return@forEach
            val person = state.commanders.firstOrNull { it.id == id } ?: return@forEach
            val detail = state.court.characters.firstOrNull { it.commanderId == id && it.alive } ?: return@forEach
            if (state.commanderAway(id) || person.loyalty < 30) return@forEach
            val choice = case.options.maxBy { recommendationScore(state,it,office,detail.personality) }
            list += CouncilRecommendation(person.name, office.label, choice.id,
                "${detail.personality.label}, Loyalität ${person.loyalty}; als ${office.label} gewichte ich ${choice.explanation}")
        }
        return list
    }

    /** Reconcile presentation after an action without spending resources or running another day. */
    fun refreshRegency(state: GameState): GameState {
        val status = regency(state)
        return state.copy(coRuler = state.coRuler.copy(actingRuler = status.actor, regencyEfficiency = status.efficiency))
    }

    fun regency(state: GameState): RegencyStatus {
        val presence = PresenceEngine.presence(state)
        fun home(p: PersonPresence) = p.available && p.location in listOf(PresenceLocation.PALACE,PresenceLocation.CITY,PresenceLocation.WALL)
        if (home(presence.player)) return RegencyStatus(state.player.name,100,"Der Herrscher ist vor Ort.")
        if (isCoRuler(state) && home(presence.companion)) return RegencyStatus(state.companion.name,100,
            "Die Mitregentin führt zu Hause ihr Ressort; Vollmachten bleiben begrenzt.",true)
        val regentId = state.dynasty.regentCommanderId
        val regent = state.commanders.firstOrNull { it.id == regentId && it.id != COMPANION_COMMANDER_ID &&
            !state.commanderAway(it.id) && it.loyalty >= 30 && state.court.characters.any { d -> d.commanderId == it.id && d.alive } }
        return if (regent != null) RegencyStatus(regent.name,70,"Der bestimmte Regent übernimmt; Anweisungen werden mit 70 % Wirkung umgesetzt.")
        else RegencyStatus("Hofrat",60,"Beide sind abwesend oder nicht verfügbar; der Hofrat verwaltet mit 60 % Wirkung.")
    }

    fun setPortfolio(state: GameState, portfolio: CoRulerPortfolio): GameEngine.ActionResult {
        if (!isCoRuler(state)) return GameEngine.ActionResult(state,"Ein Ressort benötigt die bewusste Ernennung zur Mitregentin.")
        if (state.battleSession?.isActive == true) return GameEngine.ActionResult(state,"Das Ressort kann nach der Schlacht geändert werden.")
        return GameEngine.ActionResult(state.copy(coRuler=state.coRuler.copy(portfolio=portfolio)),"${state.companion.name} führt ${portfolio.label}; es entsteht kein pauschaler Bonus.")
    }

    fun setDelegation(state: GameState, policy: DelegationPolicy): GameEngine.ActionResult {
        if (!isCoRuler(state)) return GameEngine.ActionResult(state,"Vollmachten benötigen eine ernannte Mitregentin.")
        if (state.battleSession?.isActive == true) return GameEngine.ActionResult(state,"Vollmachten nach der Schlacht ändern.")
        if (policy.maxGoldPerAction !in 0..500 || policy.maxGoldPerDay !in 0..500 || policy.maxGoldPerAction > policy.maxGoldPerDay)
            return GameEngine.ActionResult(state,"Vollmachten benötigen 0–500 Gold pro Aktion und Tag; Aktionslimit darf das Tageslimit nicht übersteigen.")
        return GameEngine.ActionResult(state.copy(coRuler=state.coRuler.copy(delegation=policy)),
            if (policy.enabled) "Nur ausgewählte Verwaltungsaktionen sind freigegeben; Kriege bleiben deine Entscheidung." else "Autonome Entscheidungen sind pausiert.")
    }

    private fun affordable(state: GameState, option: CouncilOption): Boolean = ResourceKind.entries.all {
        it.value(option.cost) >= 0 && it.value(state.resources) >= it.value(option.cost)
    }

    fun optionBlocker(state: GameState, caseId: String, optionId: String): String? {
        if (state.battleSession?.isActive == true) return "Erst die laufende Schlacht entscheiden."
        if (state.war.playerCondition != CombatantStatus.ACTIVE) return "Der Herrscher muss gesund und frei entscheiden können."
        val case = councilCases(state).firstOrNull { it.id == caseId } ?: return "Diese Ratsfrage ist bereits entschieden oder nicht mehr aktuell."
        val option = case.options.firstOrNull { it.id == optionId } ?: return "Unbekannte Ratsoption."
        if (!affordable(state,option)) return "Es fehlen ${ResourceKind.entries.filter { it.value(state.resources)<it.value(option.cost) }.joinToString { "${it.value(option.cost)} ${it.label}" }}."
        if (option.effects.healingDays>0 && state.war.wounded.isEmpty()) return "Keine verwundete Kohorte benötigt zusätzliche Genesung."
        return null
    }

    fun decide(state: GameState, caseId: String, optionId: String, respectful: Boolean = true): GameEngine.ActionResult {
        optionBlocker(state,caseId,optionId)?.let { return GameEngine.ActionResult(state,it) }
        val case = councilCases(state).first { it.id == caseId }
        val choice = case.options.first { it.id == optionId }
        var next = apply(state,case,choice,state.player.name,"Nach Anhörung des Rates: ${choice.explanation}",false,100)
        val companionVote = case.recommendations.firstOrNull { it.member == state.companion.name && it.role.startsWith("Mitregentin") }
        if (!respectful && companionVote != null && companionVote.optionId != optionId) {
            val ignored = state.coRuler.disrespectfulOverrides+1
            next = next.copy(coRuler=next.coRuler.copy(disrespectfulOverrides=ignored))
            if (ignored>=3) {
                val existing = next.relationship.issues.firstOrNull { it.id == "governance_recognition" && !it.resolved }
                val issue = existing?.copy(severity=(existing.severity+8).coerceAtMost(100),ignoredCount=existing.ignoredCount+1)
                    ?: RelationshipIssue("governance_recognition","Hofanerkennung",30,state.day,ignoredCount=ignored)
                next = next.copy(relationship=next.relationship.copy(conflict=(next.relationship.conflict+3).coerceAtMost(100),
                    issues=next.relationship.issues.filterNot { it.id == issue.id }+issue))
            }
        }
        return GameEngine.ActionResult(next,"${case.title}: ${choice.label} umgesetzt. Ein sachliches Überstimmen schadet der Beziehung nicht.")
    }

    private fun apply(state: GameState, case: CouncilCase, choice: CouncilOption, actor: String, reason: String,
        autonomous: Boolean, efficiency: Int): GameState {
        fun scaled(value: Int): Int = value*efficiency/100
        fun stat(value: Int,delta: Int) = (value+scaled(delta)).coerceIn(0,100)
        val afterCost = ResourceKind.entries.fold(state.resources) { stock,kind -> kind.withValue(stock,kind.value(stock)-kind.value(choice.cost)) }
        val reward = ResourceKind.entries.fold(Resources(0,0,0,0,0)) { stock,kind -> kind.withValue(stock,scaled(kind.value(choice.reward))) }
        val e=choice.effects
        val record=GovernmentDecisionRecord(case.id,state.day,case.title,choice.label,actor,reason,autonomous,choice.cost.gold,efficiency)
        val fieldId=state.world.playerFieldArmies.minByOrNull { it.supplyDays }?.id
        var next=state.copy(
            resources=EconomyEngine.addCapped(afterCost,reward,state.city.storageCapacity),
            city=state.city.copy(satisfaction=stat(state.city.satisfaction,e.satisfaction),security=stat(state.city.security,e.security),
                prosperity=stat(state.city.prosperity,e.prosperity),taxLevel=e.taxLevel?:state.city.taxLevel,workerPriority=e.priority?:state.city.workerPriority),
            realm=state.realm.copy(wallIntegrity=stat(state.realm.wallIntegrity,e.wallRepair)),
            society=state.society.copy(culturalTension=stat(state.society.culturalTension,e.culturalTension),
                warExhaustion=stat(state.society.warExhaustion,e.warExhaustion),politicalLoyalty=stat(state.society.politicalLoyalty,e.loyalty)),
            companion=state.companion.copy(respect=stat(state.companion.respect,e.companionRespect)),
            commanders=state.commanders.map { if (state.commanderAway(it.id)) it else it.copy(loyalty=stat(it.loyalty,e.loyalty)) },
            armyPools=state.armyPools.map { it.copy(morale=stat(it.morale,e.morale)) },
            occupations=state.occupations.map { it.copy(unrest=stat(it.unrest,e.occupationUnrest)) },
            war=state.war.copy(wounded=state.war.wounded.map { it.copy(recoveryDay=(it.recoveryDay-scaled(e.healingDays)).coerceAtLeast(state.day+1)) }),
            world=state.world.copy(armies=state.world.armies.map { if(it.id==fieldId && e.fieldSupply>0)
                it.copy(supplyFood=(it.supplyFood.toLong()+e.fieldSupply).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) else it }),
            coRuler=state.coRuler.copy(pendingCaseIds=selectedCases(state).map { it.id }-case.id,agendaDay=state.day,
                resolvedDays=state.coRuler.resolvedDays+(case.id to state.day),decisions=(state.coRuler.decisions+record).takeLast(120),
                spendingDay=if(autonomous)state.day else state.coRuler.spendingDay,
                goldSpentToday=if(autonomous)(if(state.coRuler.spendingDay==state.day)state.coRuler.goldSpentToday else 0)+choice.cost.gold else state.coRuler.goldSpentToday),
        )
        if(e.diplomaticRelation!=0) diplomaticTarget(next)?.let { target ->
            next=DiplomacyEngine.changeRelation(next,PLAYER_FACTION,target.id,scaled(e.diplomaticRelation),2,
                "$actor: ${case.title} – ${choice.label}")
        }
        val joint=isCoRuler(state)&&!autonomous
        next=next.copy(chronicle=(next.chronicle+ChronicleEntry(state.day,
            if(joint)"Das Herrscherpaar entscheidet" else "$actor entscheidet", "${case.title}: ${choice.label}. $reason")).takeLast(2000))
        if(joint) next=RelationshipEngine.remember(next,"shared_governance","${case.title}: ${choice.label}; ihr habt unterschiedliche Prioritäten im Rat gehört.",1,setOf("Politik","Mitregentschaft"))
        return next
    }

    fun day(state: GameState): GameState {
        if(state.coRuler.lastTickDay>=state.day)return state
        val regency=regency(state)
        var next=state.copy(coRuler=state.coRuler.copy(lastTickDay=state.day,actingRuler=regency.actor,
            regencyEfficiency=regency.efficiency,spendingDay=state.day,
            goldSpentToday=if(state.coRuler.spendingDay==state.day)state.coRuler.goldSpentToday else 0))
        next=next.copy(coRuler=next.coRuler.copy(pendingCaseIds=selectedCases(next).map { it.id },agendaDay=state.day))
        val policy=next.coRuler.delegation
        if(!isCoRuler(next)||!policy.enabled||next.battleSession?.isActive==true)return next
        val p=PresenceEngine.presence(next)
        val companionHome=p.companion.available&&p.companion.location in listOf(PresenceLocation.PALACE,PresenceLocation.CITY,PresenceLocation.WALL)
        // If the ruler is available at home, an absent companion's personal delegation pauses.
        // With both absent the established regent/council carries out only the same approved safe remit.
        if(!companionHome&&p.player.available)return next
        val actor=if(companionHome)next.companion.name else regency.actor
        val efficiency=if(companionHome)100 else regency.efficiency
        val case=councilCases(next).filter { it.portfolio==next.coRuler.portfolio }
            .sortedByDescending { urgency(next,it) }.firstOrNull()?:return next
        if(state.day%3!=0&&urgency(next,case)<60)return next
        val choices=case.options.filter { it.delegationAction in policy.allowedActions&&it.delegationAction!=null&&affordable(next,it)&&
            it.cost.gold<=policy.maxGoldPerAction&&next.coRuler.goldSpentToday.toLong()+it.cost.gold<=policy.maxGoldPerDay&&
            (it.effects.healingDays==0||next.war.wounded.isNotEmpty()) }
        val choice=choices.maxByOrNull {
            var score=recommendationScore(next,it,companion=true)
            score+=when(policy.focus){GovernmentFocus.SOCIAL->it.effects.satisfaction*3-it.effects.warExhaustion*2
                GovernmentFocus.FISCAL->it.effects.prosperity*4-it.cost.gold/25
                GovernmentFocus.MILITARY->it.effects.security*3+it.effects.morale*2+it.effects.wallRepair}
            if(policy.stance==GovernmentStance.CAUTIOUS)score-=it.cost.gold/40
            score
        }?:return next
        return apply(next,case,choice,actor,
            "Ressort ${next.coRuler.portfolio.label}; ${policy.stance.label.lowercase()}, ${policy.focus.label.lowercase()}. " +
                "${next.relationship.personality.priorities.take(2).joinToString { it.label }}: ${choice.explanation} " +
                "${choice.cost.gold} Gold innerhalb der Vollmacht ${policy.maxGoldPerAction}/${policy.maxGoldPerDay}. " +
                if(companionHome)"${regency.reason}" else "Vertretung: ${regency.reason}",true,efficiency)
    }
}
