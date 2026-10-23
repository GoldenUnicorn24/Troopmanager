package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Living opponents in the Kronlande: rival crowns, bandits, rebels and the Aschebund
 * march against your provinces. Garrisons, fortifications and relief armies defend them,
 * and large campaigns can be fought as real live battles with Combat Engine 3.0.
 */
object CrownWarEngine {
    private fun ok(state: GameState, text: String) = GameEngine.ActionResult(state, text)
    private fun safe(value: Long): Int = value.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    private val hostile = setOf(ProvinceOwner.RIVAL, ProvinceOwner.HORDE, ProvinceOwner.BANDITS, ProvinceOwner.REBELS)

    fun militia(p: CrownProvince): Int = p.population / 50

    /** Defensive value of one of your provinces, including stationed troops, militia and walls. */
    fun defensePower(p: CrownProvince): Long =
        ((p.garrison.toLong() * p.garrisonQuality.coerceAtLeast(8) + militia(p) * 7L) * (1.0 + p.fortification * 0.25)).toLong()

    fun attackPower(t: CrownThreat): Long = t.soldiers.toLong() * t.quality

    private fun replace(state: GameState, province: CrownProvince): GameState =
        state.copy(crown = state.crown.withProvinces(state.crown.provinces.map { if (it.id == province.id) province else it }))

    // ------------------------------------------------------------ daily

    fun tick(state: GameState): GameState {
        var next = resolveArrivals(state)
        next = spawnThreats(next)
        return next
    }

    private fun interval(state: GameState): Int {
        val owned = state.crown.ownedProvinces.size
        val base = when (state.settings.difficulty) { Difficulty.STORY -> 14; Difficulty.VETERAN -> 7; else -> 10 }
        return (base - owned / 6).coerceAtLeast(4)
    }

    private fun spawnThreats(state: GameState): GameState {
        val owned = state.crown.ownedProvinces
        if (owned.isEmpty() || state.day < 10 || state.crown.godMode) return state
        if (state.day - state.crown.lastThreatDay < interval(state)) return state
        if (state.crown.threats.size >= 1 + owned.size / 10) return state
        val targeted = state.crown.threats.map { it.targetId }.toSet()
        val pairs = state.crown.provinces.filter { it.owner in hostile && it.garrison >= 200 }.flatMap { source ->
            owned.filter { target ->
                target.id !in targeted && (target.land == source.land || (CrownCatalog.neighbors[source.land] ?: emptySet()).contains(target.land))
            }.map { source to it }
        }
        if (pairs.isEmpty()) return state.copy(crown = state.crown.copy(lastThreatDay = state.day))
        val rng = Random(state.day * 4099 + owned.size * 131)
        // Attackers prefer weak, rich provinces.
        val (source, target) = pairs.sortedBy { (_, t) -> defensePower(t) / (t.wealth + 1) }.take(4).let { it[rng.nextInt(it.size)] }
        val horde = if (source.owner == ProvinceOwner.HORDE) owned.size * 120 else 0
        val soldiers = (source.garrison * 0.55).toInt() + horde
        val days = 4
        val threat = CrownThreat(
            id = "threat_${state.crown.nextId}", targetId = target.id, sourceId = source.id, attacker = source.owner,
            soldiers = soldiers, quality = source.garrisonQuality, announcedDay = state.day, arrivalDay = state.day + days,
        )
        var next = replace(state, source.copy(garrison = (source.garrison - (source.garrison * 0.55).toInt()).coerceAtLeast(0)))
        next = next.copy(crown = next.crown.copy(threats = next.crown.threats + threat, lastThreatDay = state.day, nextId = next.crown.nextId + 1))
        return CrownEngine.chronicle(next, "Gegenoffensive gegen ${target.name}",
            "${source.owner.label} aus ${source.name} marschiert mit ${soldiers} Kriegern auf ${target.name}. Ankunft in $days Tagen. Stationiere Truppen, befestige die Provinz oder zieh dem Feind mit dem Heer entgegen.")
    }

    private fun resolveArrivals(state: GameState): GameState {
        var next = state
        state.crown.threats.filter { it.arrivalDay <= state.day }.forEach { t ->
            next = next.copy(crown = next.crown.copy(threats = next.crown.threats.filterNot { it.id == t.id }))
            val p = CrownEngine.province(next, t.targetId) ?: return@forEach
            if (p.owner != ProvinceOwner.PLAYER) return@forEach
            val roll = Random(state.day * 17 + t.id.hashCode()).nextDouble(0.9, 1.1)
            val attack = attackPower(t) * roll
            val defense = defensePower(p).toDouble()
            if (defense >= attack) {
                val loss = if (defense <= 0) 0 else (p.garrison * (attack / defense * 0.35)).toInt()
                next = replace(next, p.copy(garrison = (p.garrison - loss).coerceAtLeast(0), loyalty = (p.loyalty + 5).coerceAtMost(100)))
                next = next.copy(renown = next.renown + 10 + t.soldiers / 100)
                next = CrownEngine.chronicle(next, "${p.name} hält stand", "Der Angriff von ${t.attacker.label} (${t.soldiers} Krieger) zerbricht an Mauern, Miliz und Besatzung. Verluste der Besatzung: $loss.")
            } else {
                val survivors = (t.soldiers * (1.0 - (defense / attack.coerceAtLeast(1.0)) * 0.5)).toInt().coerceAtLeast(1)
                next = replace(next, p.copy(owner = t.attacker, garrison = survivors, baseGarrison = survivors, garrisonQuality = t.quality,
                    loyalty = 30, unrest = 0, acquiredDay = null, favor = 0))
                next = next.copy(realm = next.realm.copy(territory = (next.realm.territory - 1).coerceAtLeast(1)))
                next = CrownEngine.chronicle(next, "${p.name} gefallen", "${t.attacker.label} erobert ${p.name}. Hole dir die Provinz mit einem Feldzug zurück.")
            }
        }
        return if (next == state) state else ProgressionEngine.update(next)
    }

    // ------------------------------------------------------------ actions

    /** Moves soldiers from home into a province as a permanent garrison. */
    fun station(state: GameState, provinceId: String, amount: Int): GameEngine.ActionResult {
        val p = CrownEngine.province(state, provinceId) ?: return ok(state, "Provinz nicht gefunden.")
        if (p.owner != ProvinceOwner.PLAYER) return ok(state, "Nur in eigenen Provinzen.")
        val home = state.homeArmySize
        val n = amount.coerceAtMost(home)
        if (n <= 0) return ok(state, "Keine Soldaten daheim.")
        val losses = mutableMapOf<UnitType, Int>()
        var left = n
        // Proportional share per unit type first, then fill rounding gaps from the largest pools.
        state.armyPools.forEach { pool ->
            val take = (state.homeSoldiers(pool.type).toLong() * n / home.coerceAtLeast(1)).toInt().coerceAtMost(left)
            if (take > 0) { losses[pool.type] = take; left -= take }
        }
        state.armyPools.sortedByDescending { state.homeSoldiers(it.type) }.forEach { pool ->
            if (left <= 0) return@forEach
            val free = state.homeSoldiers(pool.type) - (losses[pool.type] ?: 0)
            val take = minOf(free, left)
            if (take > 0) { losses[pool.type] = (losses[pool.type] ?: 0) + take; left -= take }
        }
        val power = state.armyPools.sumOf { pool -> (losses[pool.type] ?: 0).toDouble() * pool.power / pool.soldiers.coerceAtLeast(1) }
        val moved = losses.values.sum()
        if (moved <= 0) return ok(state, "Keine Soldaten daheim.")
        val quality = ((p.garrison.toLong() * p.garrisonQuality + power.toLong()) / (p.garrison + moved)).toInt().coerceIn(6, 40)
        var next = CrownEngine.removeHomeSoldiers(state, losses)
        next = replace(next, p.copy(garrison = p.garrison + moved, garrisonQuality = quality))
        return ok(next, "$moved Soldaten bilden die Besatzung von ${p.name}.")
    }

    /** Brings a garrison home as basic infantry of the province's people. */
    fun withdraw(state: GameState, provinceId: String): GameEngine.ActionResult {
        val p = CrownEngine.province(state, provinceId) ?: return ok(state, "Provinz nicht gefunden.")
        if (p.owner != ProvinceOwner.PLAYER || p.garrison <= 0) return ok(state, "Hier steht keine Besatzung.")
        val type = when (p.culture) {
            Culture.HUMAN -> UnitType.HUMAN_SWORD
            Culture.WOOD_ELF -> UnitType.WOOD_BLADE
            Culture.GOLD_ELF -> UnitType.GOLD_SPEAR
            Culture.WALL -> UnitType.CRANE_GUARD
        }
        var next = CrownEngine.addSoldiers(state, type, p.garrison, 30, 75)
        next = replace(next, p.copy(garrison = 0))
        return ok(next, "${p.garrison} Soldaten kehren als ${type.label} heim.")
    }

    fun relievePreview(state: GameState, threatId: String, share: Int): CrownEngine.CampaignPreview? {
        val t = state.crown.threats.firstOrNull { it.id == threatId } ?: return null
        val own = state.armyPools.sumOf { pool ->
            val n = state.homeSoldiers(pool.type).toLong() * share / 100
            (n.toDouble() * pool.power / pool.soldiers.coerceAtLeast(1)).toLong()
        }
        val troops = state.armyPools.sumOf { (state.homeSoldiers(it.type).toLong() * share / 100).toInt() }
        val factor = 1.0 + state.player.leadership / 500.0 + state.player.tactics / 500.0 +
            (state.crown.arts.level(CombatArt.WARCRY) + state.crown.arts.level(CombatArt.STRATEGY)) * 0.02
        val enemy = attackPower(t)
        val ratio = if (enemy <= 0) 99.0 else own * factor / enemy
        val loss = if (ratio >= 1.0) (0.25 / ratio.pow(1.15)).coerceIn(0.01, 0.4) else (0.25 + 0.2 * (1.0 - ratio)).coerceIn(0.25, 0.6)
        return CrownEngine.CampaignPreview(troops, (own * factor).toLong(), t.soldiers, enemy, ratio, (troops * loss).roundToInt(),
            (troops / 10).coerceAtLeast(30), (troops / 3).coerceAtLeast(60),
            if (ratio >= 1.4) "Klarer Vorteil" else if (ratio >= 1.0) "Knapp überlegen" else "Unterlegen")
    }

    /** Intercepts a marching enemy with part of the home army before it reaches the province. */
    fun relieve(state: GameState, threatId: String, share: Int): GameEngine.ActionResult {
        val t = state.crown.threats.firstOrNull { it.id == threatId } ?: return ok(state, "Dieser Angriff ist bereits vorbei.")
        if (state.battleSession?.isActive == true) return ok(state, "Beende zuerst die laufende Schlacht.")
        val preview = relievePreview(state, threatId, share.coerceIn(1, 100)) ?: return ok(state, "Nicht möglich.")
        if (preview.ownTroops <= 0) return ok(state, "Keine Soldaten daheim.")
        if (state.resources.gold < preview.supplyGold || state.resources.food < preview.supplyFood)
            return ok(state, "Nachschub fehlt: ${preview.supplyGold} Gold und ${preview.supplyFood} Nahrung.")
        val roll = Random(state.day * 53 + t.id.hashCode()).nextDouble(0.92, 1.08)
        val ratio = preview.ratio * roll
        val victory = ratio >= 1.0
        val lossFraction = if (victory) (0.25 / ratio.pow(1.15)).coerceIn(0.01, 0.4) else (0.25 + 0.2 * (1.0 - ratio)).coerceIn(0.25, 0.6)
        val losses = state.armyPools.associate { it.type to ((state.homeSoldiers(it.type).toLong() * share / 100) * lossFraction).roundToInt() }
        var next = state.copy(resources = state.resources.copy(gold = state.resources.gold - preview.supplyGold, food = state.resources.food - preview.supplyFood))
        next = CrownEngine.removeHomeSoldiers(next, losses)
        val target = CrownEngine.province(next, t.targetId)?.name ?: "die Provinz"
        val text: String
        if (victory) {
            next = next.copy(
                crown = next.crown.copy(threats = next.crown.threats.filterNot { it.id == t.id }),
                renown = next.renown + 15 + t.soldiers / 60, victories = next.victories + 1,
            )
            text = "Entsatz für $target: Das feindliche Heer (${t.soldiers}) wird im Feld zerschlagen. Eigene Verluste: ${losses.values.sum()}."
        } else {
            val enemyLeft = (t.soldiers * (1.0 - (ratio * 0.5).coerceIn(0.05, 0.6))).toInt().coerceAtLeast(1)
            next = next.copy(
                crown = next.crown.copy(threats = next.crown.threats.map { if (it.id == t.id) it.copy(soldiers = enemyLeft) else it }),
                defeats = next.defeats + 1,
            )
            text = "Der Entsatz für $target scheitert. Verluste: ${losses.values.sum()}. Der Feind marschiert mit $enemyLeft Kriegern weiter."
        }
        return ok(CrownEngine.chronicle(next, if (victory) "Entsatzschlacht gewonnen" else "Entsatz gescheitert", text), text)
    }

    // ------------------------------------------------------------ live battles

    fun enemyTypeFor(owner: ProvinceOwner): EnemyType = when (owner) {
        ProvinceOwner.HORDE -> EnemyType.ORC
        ProvinceOwner.REBELS, ProvinceOwner.BANDITS -> EnemyType.TAO_TEI
        else -> EnemyType.URUK
    }

    /** Fights the conquest of a province as a real live battle with Combat Engine 3.0. */
    fun startLiveCampaign(state: GameState, provinceId: String): GameEngine.ActionResult {
        if (state.battleSession != null) return ok(state, "Beende zuerst die laufende Schlacht.")
        val p = CrownEngine.province(state, provinceId) ?: return ok(state, "Provinz nicht gefunden.")
        if (p.owner == ProvinceOwner.PLAYER) return ok(state, "${p.name} gehört bereits dir.")
        if (!CrownEngine.reachable(state, p.land)) return ok(state, "${p.land.label} ist noch nicht erreichbar.")
        if (p.garrison <= 0) return ok(state, "${p.name} ist unverteidigt. Ein normaler Feldzug genügt.")
        if (CrownEngine.campaignsLeft(state) <= 0) return ok(state, "Dein Heer braucht Ruhe. Morgen sind neue Feldzüge möglich.")
        val preview = CrownEngine.preview(state, provinceId, 100) ?: return ok(state, "Feldzug nicht möglich.")
        if (preview.ownTroops <= 0) return ok(state, "Keine Soldaten daheim.")
        if (state.resources.gold < preview.supplyGold || state.resources.food < preview.supplyFood)
            return ok(state, "Nachschub fehlt: ${preview.supplyGold} Gold und ${preview.supplyFood} Nahrung.")
        val paid = state.copy(resources = state.resources.copy(gold = state.resources.gold - preview.supplyGold, food = state.resources.food - preview.supplyFood))
        val result = BattleEngine.start(
            paid, enemyTypeFor(p.owner), Tactic.AGGRESSIVE,
            enemyStrength = p.garrison.coerceAtLeast(1), location = p.name,
            enemyArmyName = "Besatzung von ${p.name}", enemyFortification = p.fortification * 20,
            enemyFactionName = p.owner.label, enemyExperience = (p.garrisonQuality * 3).coerceAtMost(90),
        )
        if (result.state.battleSession == null) return ok(state, result.message)
        val crown = result.state.crown
        val next = result.state.copy(crown = crown.copy(
            liveCampaignProvinceId = provinceId,
            lastCampaignDay = state.day,
            campaignsToday = (if (crown.lastCampaignDay == state.day) crown.campaignsToday else 0) + 1,
        ))
        return ok(next, "Die Schlacht um ${p.name} beginnt. Führe deine Fronten selbst!")
    }

    /** Applies the outcome of a finished live campaign battle exactly once. */
    fun reconcile(state: GameState): GameState {
        val id = state.crown.liveCampaignProvinceId ?: return state
        val session = state.battleSession
        if (session == null) return state.copy(crown = state.crown.copy(liveCampaignProvinceId = null))
        if (session.isActive) return state
        val p = CrownEngine.province(state, id) ?: return state.copy(crown = state.crown.copy(liveCampaignProvinceId = null))
        var next = state.copy(crown = state.crown.copy(liveCampaignProvinceId = null))
        val enemyLosses = (session.enemyStart - session.enemyRemaining).coerceAtLeast(0)
        val ownLosses = (session.ownStart - session.ownRemaining).coerceAtLeast(0)
        val text: String
        if (session.status == BattleStatus.VICTORY) {
            val loot = safe(p.wealth.toLong() * p.population / 12 + p.garrison * 2L)
            next = next.copy(
                resources = next.resources.copy(gold = safe(next.resources.gold.toLong() + loot)),
                crown = next.crown.copy(campaignVictories = next.crown.campaignVictories + 1),
            )
            next = CrownEngine.acquire(next, id, "Eroberung")
            text = "Sieg in der Schlacht um ${p.name}! Beute: $loot Gold."
            next = next.copy(crown = next.crown.copy(reports = (listOf(CampaignReport(state.day, p.id, p.name, true, session.ownStart, ownLosses, session.enemyStart, enemyLosses, loot, 0, text)) + next.crown.reports).take(25)))
        } else {
            next = replace(next, p.copy(garrison = (p.garrison - enemyLosses).coerceAtLeast(0)))
            text = "Die Schlacht um ${p.name} ist verloren. Die Besatzung verlor $enemyLosses Mann."
            next = next.copy(crown = next.crown.copy(reports = (listOf(CampaignReport(state.day, p.id, p.name, false, session.ownStart, ownLosses, session.enemyStart, enemyLosses, 0, 0, text)) + next.crown.reports).take(25)))
        }
        return CrownEngine.chronicle(next, if (session.status == BattleStatus.VICTORY) "Provinz erstürmt" else "Sturm abgewiesen", text)
    }
}
