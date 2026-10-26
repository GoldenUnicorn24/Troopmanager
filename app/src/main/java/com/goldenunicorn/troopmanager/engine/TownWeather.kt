package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/**
 * Weather of the seven towns for the personal layer. Stateless and deterministic (day, town,
 * season), so landless games – whose strategic world sleeps – still have rain, fog and snow, and
 * nothing new has to be saved. Weather lasts two days at a time and follows the season and the
 * character of the place (storms at the free port, fog in the elven woods).
 */
object TownWeather {
    fun season(day: Int): Season = Season.forDay(day)

    fun today(day: Int, provinceId: String): WeatherKind {
        val block = day.coerceAtLeast(1) / 2
        val roll = Math.floorMod(provinceId.hashCode() * 31 + block * 7919 + block * block * 13, 100)
        val coast = provinceId == "ks_saltmere"
        val forest = provinceId.startsWith("wh_")
        val table: List<Pair<WeatherKind, Int>> = when (season(day)) {
            Season.SPRING -> listOf(WeatherKind.RAIN to 28, WeatherKind.FOG to (if (forest) 22 else 10), WeatherKind.STORM to (if (coast) 12 else 4))
            Season.SUMMER -> listOf(WeatherKind.HEAT to 24, WeatherKind.STORM to (if (coast) 14 else 7), WeatherKind.RAIN to 10, WeatherKind.FOG to (if (forest) 10 else 0))
            Season.AUTUMN -> listOf(WeatherKind.RAIN to 30, WeatherKind.FOG to (if (forest) 28 else 18), WeatherKind.STORM to (if (coast) 16 else 8))
            Season.WINTER -> listOf(WeatherKind.SNOW to (if (coast) 12 else 30), WeatherKind.FROST to 24, WeatherKind.STORM to (if (coast) 14 else 5), WeatherKind.FOG to 8)
        }
        var acc = 0
        for ((kind, share) in table) { acc += share; if (roll < acc) return kind }
        return WeatherKind.CLEAR
    }

    fun here(state: GameState): WeatherKind = today(state.day, state.life.provinceId)

    /** Weather that drives people indoors. */
    fun foul(kind: WeatherKind): Boolean = kind == WeatherKind.RAIN || kind == WeatherKind.STORM || kind == WeatherKind.SNOW

    fun describe(kind: WeatherKind): String = when (kind) {
        WeatherKind.CLEAR -> "klarer Himmel"
        WeatherKind.RAIN -> "Regen trommelt auf die Dächer"
        WeatherKind.STORM -> "Sturm reißt an den Fensterläden"
        WeatherKind.FOG -> "Nebel liegt in den Gassen"
        WeatherKind.SNOW -> "Schnee dämpft jeden Schritt"
        WeatherKind.FROST -> "klirrender Frost"
        WeatherKind.HEAT -> "drückende Hitze"
    }

    /** Small talk about the weather, used by the dialogue placeholder {weather}. */
    fun smallTalk(kind: WeatherKind): String = when (kind) {
        WeatherKind.CLEAR -> "Endlich mal Sonne. Da kriegt man glatt Lust, irgendwohin zu reiten."
        WeatherKind.RAIN -> "Bei dem Regen ist die halbe Stadt hier drin. Gut fürs Geschäft, schlecht für die Ernte."
        WeatherKind.STORM -> "Hörst du das? Bei dem Sturm verlässt keiner, der bei Verstand ist, die Stadt."
        WeatherKind.FOG -> "Nebel wie Milch. Auf den Straßen lauern sie bei so einem Wetter gern."
        WeatherKind.SNOW -> "Schnee bis zu den Knien. Die Wege sind teuer, wer jetzt reist, zahlt doppelt."
        WeatherKind.FROST -> "Die Kälte kriecht einem in die Knochen. Ein Becher Gewürzwein, und es geht."
        WeatherKind.HEAT -> "Diese Hitze! Selbst das Bier wird warm, bevor man's trinkt."
    }
}
