package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

/** Milestone 6: weather in the towns, local prices, honest odds before a fight. */
class RebirthWorldTest {
    private fun start(sword: Int = 60): GameState = LifeEngine.newGame(LifeEngine.StartConfig(
        mode = GameMode.FREE_LIFE, name = "Leon", age = 24, gender = Gender.MALE, species = Species.HUMAN, culture = Culture.HUMAN,
        background = Background.MERCENARY, profession = "", wealth = WealthLevel.MODEST, homeProvinceId = "gm_rabenfurt",
        attributes = GameEngine.StartingAttributes(sword = sword, bow = 40, riding = 40, leadership = 40, tactics = 30, diplomacy = 60),
        companionChoice = CompanionChoice.NONE,
    ))

    private fun dayWith(prov: String, kind: WeatherKind, from: Int = 1): Int =
        (from..2000).first { TownWeather.today(it, prov) == kind }

    @Test fun weatherIsDeterministicSeasonalAndLocal() {
        val days = 1..360
        Towns.all.forEach { t -> days.forEach { d -> assertEquals(TownWeather.today(d, t.provinceId), TownWeather.today(d, t.provinceId)) } }
        // Weather holds for two days.
        assertEquals(TownWeather.today(10, "gm_rabenfurt"), TownWeather.today(11, "gm_rabenfurt"))
        val summer = (31..60)
        val winter = (91..120)
        assertTrue(Towns.all.none { t -> summer.any { TownWeather.today(it, t.provinceId) == WeatherKind.SNOW } })
        assertTrue(Towns.all.any { t -> winter.any { TownWeather.today(it, t.provinceId) == WeatherKind.SNOW } })
        val coastStorms = days.count { TownWeather.today(it, "ks_saltmere") == WeatherKind.STORM }
        val inlandStorms = days.count { TownWeather.today(it, "gm_eschenhof") == WeatherKind.STORM }
        assertTrue("coast $coastStorms inland $inlandStorms", coastStorms > inlandStorms)
        // Not every day is grey.
        assertTrue(days.count { TownWeather.today(it, "gm_rabenfurt") == WeatherKind.CLEAR } > 60)
    }

    @Test fun foulWeatherDrivesTheTownsfolkIntoTheTavern() {
        val s = start()
        val rainy = dayWith("gm_rabenfurt", WeatherKind.RAIN)
        val e = s.copy(day = rainy, life = s.life.copy(timeSlot = TimeSlot.AFTERNOON))
        val locals = e.life.npcs.filter { it.homeProvinceId == "gm_rabenfurt" && it.role in setOf(NpcRole.REGULAR, NpcRole.TRAVELER) }
        locals.forEach { n ->
            val (_, place) = NpcEngine.placeOf(n, rainy, TimeSlot.AFTERNOON, e)
            assertTrue("${n.name} at $place", place != LifePlace.TOWN && place != LifePlace.MARKET)
        }
        assertTrue(TavernEngine.present(e.copy(life = e.life.copy(place = LifePlace.TAVERN))).isNotEmpty())
    }

    @Test fun stormsCloseTheMarketAndKeepTravellersHome() {
        val s = start()
        val stormy = dayWith("ks_saltmere", WeatherKind.STORM)
        val port = s.copy(day = stormy, life = s.life.copy(provinceId = "ks_saltmere", timeSlot = TimeSlot.MORNING, purse = 100))
        assertFalse(MarketEngine.open(port))
        assertNotNull(LifeEngine.travelBlocker(port, "gm_rabenfurt"))
        val clear = dayWith("ks_saltmere", WeatherKind.CLEAR)
        val calm = port.copy(day = clear)
        assertTrue(MarketEngine.open(calm))
        // Snow makes the road dearer, fog makes it more dangerous.
        val snowy = dayWith("gm_rabenfurt", WeatherKind.SNOW)
        val home = s.copy(life = s.life.copy(purse = 100))
        val clearHome = home.copy(day = dayWith("gm_rabenfurt", WeatherKind.CLEAR))
        assertTrue(LifeEngine.travelCost(home.copy(day = snowy), "gm_eschenhof") > LifeEngine.travelCost(clearHome, "gm_eschenhof"))
        val foggy = dayWith("gm_rabenfurt", WeatherKind.FOG)
        assertTrue(LifeEngine.ambushChance(home.copy(day = foggy), "gm_eschenhof") > LifeEngine.ambushChance(clearHome, "gm_eschenhof"))
    }

    @Test fun everyTownHasItsOwnPricesAndTheTradeTipTellsTheTruth() {
        val s = start().let { it.copy(life = it.life.copy(timeSlot = TimeSlot.MORNING)) }
        val armor = GearCatalog.armor[3].price
        val alden = s.copy(life = s.life.copy(provinceId = "al_aldenburg"))
        val elves = s.copy(life = s.life.copy(provinceId = "wh_mondquell"))
        assertTrue(MarketEngine.price(alden, MarketEngine.Stall.SMITH, armor) < MarketEngine.price(elves, MarketEngine.Stall.SMITH, armor))
        assertTrue(MarketEngine.healPrice(elves) <= MarketEngine.healPrice(alden))
        assertEquals("al_aldenburg", MarketEngine.cheapestTown(MarketEngine.Stall.SMITH))
        // Buying charges exactly the shown price.
        val rich = alden.copy(life = alden.life.copy(purse = 500))
        val bought = MarketEngine.buyArmor(rich, 3).state
        assertEquals(500 - MarketEngine.price(rich, MarketEngine.Stall.SMITH, armor), bought.life.purse)
        // The tip names a real cheapest town.
        repeat(3) { d ->
            val tip = NpcSocialEngine.tradeTip(s.copy(day = d))
            assertTrue(tip, Towns.all.any { tip.contains(CrownCatalog.info(it.provinceId).name) })
        }
    }

    @Test fun oddsAreHonestDeterministicAndChangeNothing() {
        val weak = start(sword = 35)
        val strong = start(sword = 80).let { it.copy(life = it.life.copy(gear = PlayerGear(3, 4))) }
        val a = SkirmishEngine.odds(weak, SkirmishKind.CAMPAIGN, 6, 70)
        val b = SkirmishEngine.odds(strong, SkirmishKind.CAMPAIGN, 6, 70)
        assertTrue("weak $a strong $b", b > a)
        assertEquals(a, SkirmishEngine.odds(weak, SkirmishKind.CAMPAIGN, 6, 70))
        assertTrue(a in 0..100 && b in 0..100)
        // Asking costs nothing: no fight starts, no id is used.
        assertNull(weak.life.skirmish)
        assertEquals("sehr gut", SkirmishEngine.oddsLabel(90))
        assertEquals("aussichtslos", SkirmishEngine.oddsLabel(5))
    }

    @Test fun peopleTalkAboutTheWeather() {
        val s = start().let { it.copy(life = it.life.copy(timeSlot = TimeSlot.EVENING, place = LifePlace.TAVERN)) }
        val keeper = s.life.npcs.first { it.role == NpcRole.INNKEEPER && it.homeProvinceId == s.life.provinceId }
        val r = DialogueEngine.choose(s, keeper.id, "talk_weather")
        assertEquals(DialogueEngine.Outcome.SUCCESS, r.outcome)
        assertTrue(r.npcLine.contains(TownWeather.smallTalk(TownWeather.here(s))))
    }

    @Test fun lordsHandOutDeedsWithPauses() {
        var s = CastleEngine.enter(start().let { it.copy(life = it.life.copy(renown = 40, timeSlot = TimeSlot.MORNING)) }).state
        s = CastleEngine.askForTask(s).state
        val q = s.life.lordQuest!!
        s = CastleEngine.ride(s).state
        s = SkirmishEngine.close(s.copy(life = s.life.copy(skirmish = s.life.skirmish!!.copy(outcome = SkirmishOutcome.VICTORY)))).state
        assertNull(s.life.lordQuest)
        val again = CastleEngine.askForTask(s.copy(life = s.life.copy(timeSlot = TimeSlot.MORNING)))
        assertNull(again.state.life.lordQuest)
        assertTrue(again.message, again.message.contains("Tagen"))
        val later = s.copy(day = s.day + CastleEngine.QUEST_PAUSE, life = s.life.copy(timeSlot = TimeSlot.MORNING))
        val next = CastleEngine.askForTask(later).state.life.lordQuest
        assertNotNull(next)
        assertNotEquals(q.targetProvinceId, next!!.targetProvinceId)
    }
}
