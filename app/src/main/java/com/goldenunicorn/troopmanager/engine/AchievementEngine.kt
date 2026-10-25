package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/** 30 achievements with small rewards – a second layer of goals next to the acts. */
object AchievementEngine {
    data class Achievement(val id: String, val title: String, val text: String, val gold: Int, val renown: Int, val done: (GameState) -> Boolean)

    val all: List<Achievement> = listOf(
        Achievement("first_victory", "Erster Sieg", "Gewinne deine erste Schlacht.", 500, 5) { it.victories >= 1 },
        Achievement("ten_victories", "Kriegsherr", "Gewinne 10 Schlachten.", 3_000, 20) { it.victories >= 10 },
        Achievement("fifty_victories", "Unbesiegbar", "Gewinne 50 Schlachten.", 20_000, 80) { it.victories >= 50 },
        Achievement("tactician", "Taktiker", "Gewinne 5 Schlachten mit eigenem Schlachtplan.", 4_000, 25) { it.reign.tacticalVictories >= 5 },
        Achievement("army_1k", "Tausend Klingen", "Führe ein Heer von 1.000 Soldaten.", 1_000, 10) { it.armySize >= 1_000 },
        Achievement("army_10k", "Zehntausend", "Führe ein Heer von 10.000 Soldaten.", 8_000, 40) { it.armySize >= 10_000 },
        Achievement("army_50k", "Ein Meer aus Speeren", "Führe ein Heer von 50.000 Soldaten.", 30_000, 120) { it.armySize >= 50_000 },
        Achievement("province_1", "Erste Krone", "Gewinne deine erste Kronprovinz.", 1_000, 10) { it.crown.ownedProvinces.isNotEmpty() },
        Achievement("province_10", "Landesherr", "Besitze 10 Kronprovinzen.", 6_000, 30) { it.crown.ownedProvinces.size >= 10 },
        Achievement("province_25", "Reichsgründer", "Besitze 25 Kronprovinzen.", 20_000, 80) { it.crown.ownedProvinces.size >= 25 },
        Achievement("province_42", "Herr aller Kronlande", "Besitze alle 42 Kronprovinzen.", 100_000, 300) { it.crown.ownedProvinces.size >= 42 },
        Achievement("act_1", "Gor-Mukar fällt", "Schließe Akt 1 ab.", 0, 0) { it.crown.act.act >= 2 },
        Achievement("act_2", "Skarnag fällt", "Schließe Akt 2 ab.", 0, 0) { it.crown.act.act >= 3 },
        Achievement("act_3", "Die Königin fällt", "Schließe Akt 3 ab.", 0, 0) { it.crown.act.act >= 4 },
        Achievement("met", "Eine Begegnung", "Triff deine zukünftige Partnerin.", 500, 5) { it.companion.met },
        Achievement("companion", "Seite an Seite", "Wirb deine Partnerin als Feldherrin an.", 1_500, 10) { s -> s.crown.saga.partner?.let { p -> s.crown.heroine(p.background)?.stage == HeroineStage.COMPANION } == true },
        Achievement("romance", "Mehr als Waffengefährten", "Eure Beziehung wird zur Romanze.", 2_000, 15) { it.relationship.romanceStage.ordinal >= RomanceStage.ROMANCE.ordinal },
        Achievement("royal", "Herrscherpaar", "Regiere gemeinsam mit deiner Königin.", 10_000, 50) { it.relationship.romanceStage == RomanceStage.CO_RULERS },
        Achievement("rich", "Volle Schatzkammer", "Besitze 100.000 Gold.", 0, 20) { it.resources.gold >= 100_000 },
        Achievement("wonder_1", "Baumeister", "Vollende einen Großbau.", 5_000, 30) { s -> s.reign.wonders.any { it.finished } },
        Achievement("wonder_4", "Wunder der Welt", "Vollende vier Großbauten.", 25_000, 100) { s -> s.reign.wonders.count { it.finished } >= 4 },
        Achievement("feast_3", "Gastgeber", "Feiere drei Königsfeste.", 3_000, 15) { it.reign.feastsHeld >= 3 },
        Achievement("king_1", "Königsbezwinger", "Besiege einen Rivalen-König.", 8_000, 40) { s -> s.crown.rivals.any { it.status == RivalStatus.DEFEATED } },
        Achievement("ally_1", "Bündnisschmied", "Schließe ein Bündnis mit einem König.", 4_000, 20) { s -> s.crown.rivals.any { it.status == RivalStatus.ALLIED } },
        Achievement("kings_all", "König der Könige", "Besiege oder verbünde dich mit allen fünf Königen.", 40_000, 150) { s -> s.crown.rivals.size >= 5 && s.crown.rivals.all { it.status == RivalStatus.DEFEATED || it.status == RivalStatus.ALLIED } },
        Achievement("trade_3", "Händlerfürst", "Unterhalte drei Handelsrouten.", 3_000, 15) { s -> s.crown.partners.sumOf { it.routes } >= 3 },
        Achievement("duelist", "Klinge des Königs", "Gewinne 5 Duelle.", 3_000, 20) { it.crown.arts.duelsWon >= 5 },
        Achievement("day_100", "Hundert Tage", "Herrsche 100 Tage.", 5_000, 25) { it.day >= 100 },
        Achievement("day_365", "Ein Jahr an der Mauer", "Herrsche 365 Tage.", 20_000, 80) { it.day >= 365 },
        Achievement("subjects_1m", "Eine Million Untertanen", "Herrsche über eine Million Menschen.", 30_000, 100) { it.crown.subjects >= 1_000_000 },
    )

    fun check(state: GameState): GameState {
        val fresh = all.filter { it.id !in state.reign.achievements && runCatching { it.done(state) }.getOrDefault(false) }
        if (fresh.isEmpty()) return state
        var next = state.copy(
            reign = state.reign.copy(achievements = state.reign.achievements + fresh.map { it.id }),
            resources = state.resources.copy(gold = (state.resources.gold.toLong() + fresh.sumOf { it.gold }).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
            renown = (state.renown.toLong() + fresh.sumOf { it.renown }).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            chronicle = state.chronicle + fresh.map { ChronicleEntry(state.day, "Erfolg: ${it.title}", it.text) },
        )
        // One overlay per day is enough; the rest is listed in the chronicle.
        val headline = fresh.first()
        next = CelebrationEngine.add(next, CelebrationKind.ACHIEVEMENT, headline.title,
            headline.text + (if (headline.gold > 0) " +${ReignEngine.fmt(headline.gold)} Gold." else "") + if (fresh.size > 1) " Und ${fresh.size - 1} weitere Erfolge." else "")
        return next
    }
}
