package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/**
 * Reichsberater: turns the simulation into a short, prioritized list of what matters today –
 * bottlenecks that silently hurt the realm and concrete things the player can do right now.
 */
object AdvisorEngine {
    enum class Severity { CRITICAL, WARNING, INFO, OPPORTUNITY }

    data class Advice(
        val severity: Severity,
        val title: String,
        val text: String,
        val destination: GameDestination,
        val tab: Int = 0,
    )

    fun advice(state: GameState): List<Advice> {
        val list = mutableListOf<Advice>()
        val net = EconomyEngine.production(state).net
        val crown = state.crown

        // ---- urgent story and combat moments
        crown.duel?.let { list += Advice(Severity.CRITICAL, "Duell läuft", "${it.opponentName} wartet auf deinen nächsten Zug.", GameDestination.KINGDOM) }
        crown.saga.pendingScene?.let { list += Advice(Severity.CRITICAL, it.title, "Eine Szene bei ${it.location} wartet auf deine Entscheidung.", GameDestination.COURT, 0) }
        state.campaign.pendingDecision?.let { list += Advice(Severity.CRITICAL, it.title, "Reichsentscheidung bis Tag ${it.expiresDay}.", GameDestination.DECISIONS) }
        crown.threats.sortedBy { it.arrivalDay }.forEach { t ->
            val p = CrownEngine.province(state, t.targetId) ?: return@forEach
            val defense = CrownWarEngine.defensePower(p)
            val attack = CrownWarEngine.attackPower(t)
            val days = (t.arrivalDay - state.day).coerceAtLeast(0)
            list += Advice(
                if (defense < attack) Severity.CRITICAL else Severity.WARNING,
                "Angriff auf ${p.name} in $days Tagen",
                "${t.soldiers} Krieger von ${t.attacker.label}. Verteidigung ${percent(defense, attack)} der Angriffskraft – " +
                    if (defense < attack) "Besatzung stationieren, befestigen oder Entsatz schicken." else "die Provinz sollte halten.",
                GameDestination.KINGDOM,
            )
        }
        if (ActEngine.bossReady(state)) ActEngine.current(state)?.let { act ->
            list += Advice(Severity.CRITICAL, "Endkampf: ${act.boss.name}", "Alle Ziele von Akt ${act.number} sind erfüllt. Stell dich ${act.boss.title} in der Schlacht.", GameDestination.KINGDOM, 0)
        }
        crown.pendingMarch?.let { m ->
            list += Advice(Severity.CRITICAL, "Feldzug wartet", "Unterwegs nach ${CrownEngine.province(state, m.provinceId)?.name ?: "der Provinz"} musst du eine Entscheidung treffen.", GameDestination.TODAY)
        }
        state.invasion?.let {
            list += Advice(Severity.CRITICAL, "${it.enemy.label} rückt an", "Ankunft an Tag ${it.arrivalDay}. Bereite Mauern und Heer vor.", GameDestination.FRONTIER)
        }

        // ---- supply bottlenecks
        if (net.food < 0) {
            val days = if (net.food == 0) Int.MAX_VALUE else state.resources.food / -net.food
            list += Advice(
                if (days < 10) Severity.CRITICAL else Severity.WARNING,
                "Nahrung reicht noch ${if (days > 999) "lange" else "$days Tage"}",
                "Täglich ${net.food} Nahrung. Hunger senkt Moral, Wachstum und Ausbildung. Baue Bauernhöfe aus, entwickle Kornprovinzen oder öffne eine Route zur Hanse von Saltmere.",
                GameDestination.CITY,
            )
        }
        val morale = if (state.armyPools.isEmpty()) 100 else (state.armyPools.sumOf { it.morale.toLong() * it.soldiers } / state.armySize.coerceAtLeast(1)).toInt()
        if (state.armySize > 0 && morale < 50)
            list += Advice(Severity.WARNING, "Moral des Heeres: $morale", "Niedrige Moral schwächt jede Schlacht. Ursachen sind meist Hunger, Niederlagen oder fehlender Sold.", GameDestination.MILITARY)
        val upkeep = CrownEngine.mercenaryUpkeep(state)
        if (upkeep > 0 && net.gold < 0)
            list += Advice(Severity.WARNING, "Gold sinkt täglich um ${-net.gold}", "Söldner kosten $upkeep Gold pro Tag. Ohne Sold desertieren sie.", GameDestination.KINGDOM, 0)

        val recruits = state.population.totalRecruits
        if (recruits >= 500) {
            val blockers = Culture.entries.mapNotNull { culture ->
                val type = UnitType.entries.firstOrNull { it.culture == culture && GameEngine.isUnitUnlocked(state, it) } ?: return@mapNotNull null
                val n = ArmyEngine.recruitable(state, culture)
                if (n <= 0) return@mapNotNull null
                val gold = (n.toLong() * type.goldCost * CrownEngine.recruitCostFactor(state)).toLong()
                val missing = MilitaryEconomyEngine.missing(state.militaryStock, MilitaryEconomyEngine.requirements(type, n))
                when {
                    gold > state.resources.gold -> "Gold (${gold - state.resources.gold} fehlen für alle ${culture.label})"
                    missing.isNotEmpty() -> missing.entries.first().let { "${it.value} ${it.key.label}" }
                    else -> null
                }
            }
            list += if (blockers.isEmpty())
                Advice(Severity.OPPORTUNITY, "$recruits Rekruten warten", "Alles bereit: Bilde sie mit der Massenausbildung aus.", GameDestination.MILITARY, 1)
            else Advice(Severity.INFO, "$recruits Rekruten warten", "Engpass: ${blockers.distinct().take(2).joinToString(" · ")}. Waffen gibt es bei Handelspartnern, Gold aus Provinzen und Handel.", GameDestination.MILITARY, 1)
        }
        ResourceKind.entries.forEach { kind ->
            val cap = kind.value(state.city.storageCapacity)
            if (cap > 0 && kind.value(state.resources) >= cap * 95L / 100 && kind.value(net) > 0)
                list += Advice(Severity.INFO, "${kind.label}lager voll", "Weitere Produktion verfällt. Baue Lagerhäuser aus oder setze ${kind.label} ein.", GameDestination.CITY)
        }
        if (state.population.total >= state.city.housingCapacity && state.crown.ownedProvinces.isEmpty())
            list += Advice(Severity.INFO, "Kein Wohnraum mehr", "Das Bevölkerungswachstum stockt. Wohnviertel oder neue Provinzen schaffen Platz.", GameDestination.CITY)

        // ---- provinces
        crown.ownedProvinces.filter { it.unrest >= 50 || it.loyalty < 25 }.take(2).forEach { p ->
            list += Advice(Severity.WARNING, "Unruhe in ${p.name}", "Loyalität ${p.loyalty}, Unruhe ${p.unrest}. Ein Volksfest oder Ausbau verhindert einen Aufstand.", GameDestination.KINGDOM)
        }

        // ---- opportunities
        if (crown.ownedProvinces.isNotEmpty() && CrownEngine.levyCooldown(state) == 0)
            list += Advice(Severity.OPPORTUNITY, "Heerbann bereit", "${CrownEngine.levyAmount(state)} neue Rekruten aus allen Kronprovinzen.", GameDestination.MILITARY, 1)
        if (CrownEngine.campaignsLeft(state) > 0 && state.homeArmySize > 0) {
            val easiest = crown.provinces.filter { it.owner != ProvinceOwner.PLAYER && CrownEngine.reachable(state, it.land) }
                .mapNotNull { p -> CrownEngine.preview(state, p.id, 100)?.let { p to it } }
                .filter { it.second.ratio >= 1.3 }.maxByOrNull { it.first.wealth * 1000 + it.first.population / 100 }
            if (easiest != null) list += Advice(Severity.OPPORTUNITY, "${easiest.first.name} ist reif",
                "Dein Heer ist ${String.format(java.util.Locale.GERMANY, "%.1f", easiest.second.ratio)}× stärker als die Besatzung. Beute und neue Untertanen warten.", GameDestination.KINGDOM)
        }
        if (crown.arts.lastTrainingDay != state.day)
            list += Advice(Severity.OPPORTUNITY, "Training möglich", "Ein Training pro Tag macht dich im Duell und als Feldherr stärker.", GameDestination.MILITARY, 2)
        if (CombatArtsEngine.tournamentCooldown(state) == 0 && state.resources.gold >= 200)
            list += Advice(Severity.OPPORTUNITY, "Turnier offen", "Gegner: ${CombatArtsEngine.tournamentOpponent(state).first}.", GameDestination.MILITARY, 2)
        crown.saga.heroines.firstOrNull { it.stage == HeroineStage.TRIAL && HeroineSagaEngine.trialProgress(state, it.id).done }?.let { h ->
            val name = HeroineSagaEngine.def(state, h.id)?.name ?: h.id
            list += Advice(Severity.OPPORTUNITY, "$name ist bereit", "Ihre Bewährungsprobe ist erfüllt. Wirb sie als Feldherrin an.", GameDestination.COURT, 0)
        }
        crown.saga.partner?.let { partner ->
            val h = crown.heroine(partner.background)
            when (h?.stage) {
                HeroineStage.TRAVELING -> {
                    val days = ((h.arrivalDay ?: state.day) - state.day).coerceAtLeast(0)
                    list += Advice(Severity.INFO, "Begegnung mit ${partner.name}", if (days == 0) "Heute kreuzen sich eure Wege." else "In $days Tagen kreuzen sich eure Wege.", GameDestination.COURT, 0)
                }
                HeroineStage.MET -> list += Advice(Severity.OPPORTUNITY, "${partner.name} wartet auf dich", "Bitte sie, deine Feldherrin zu werden.", GameDestination.COURT, 0)
                HeroineStage.TRIAL -> {
                    val progress = HeroineSagaEngine.trialProgress(state, h.id)
                    if (!progress.done) list += Advice(Severity.INFO, "Probe für ${partner.name}: ${progress.current}/${progress.target}", progress.text, GameDestination.COURT, 0)
                }
                else -> Unit
            }
        }
        if (CrownEngine.companionWoundedDays(state) > 0)
            list += Advice(Severity.INFO, "${state.companion.name} ist verwundet", "Noch ${CrownEngine.companionWoundedDays(state)} Tage, bis sie wieder mitkämpfen kann. Ein Lazarett verkürzt die Genesung.", GameDestination.COURT, 0)
        ActEngine.current(state)?.takeIf { !ActEngine.goalsMet(state) }?.let { act ->
            val open = ActEngine.goals(state).filterNot { it.done }
            list += Advice(Severity.INFO, "Akt ${act.number}: ${act.title}", open.joinToString(" · ") { "${it.text} ${it.current}/${it.target}" }, GameDestination.KINGDOM, 0)
        }
        crown.saga.goal?.let { g ->
            val p = CrownEngine.province(state, g.provinceId)
            list += Advice(Severity.INFO, g.title, "Erobere ${p?.name ?: "das Ziel"} bis Tag ${g.deadlineDay}. Belohnung: ${g.rewardGold} Gold, ${g.rewardRenown} Ruhm.", GameDestination.KINGDOM)
        }
        return list.sortedBy { it.severity.ordinal }
    }

    private fun percent(a: Long, b: Long): String = if (b <= 0) "∞" else "${(a * 100 / b)} %"
}
