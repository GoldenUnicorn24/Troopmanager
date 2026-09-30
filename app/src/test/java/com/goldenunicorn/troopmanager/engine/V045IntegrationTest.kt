package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class V045IntegrationTest {
    private fun start() = GameEngine.newGame("Update-Test",23,Species.HUMAN,null)
    @Test fun campaignDayCompletesProjectOnlyAfterRequiredDaysAndReload() {
        var state = GameEngine.build(start(),BuildingType.PALACE).state
        val days = state.city.constructionQueue.single().totalDays
        repeat(days-1) { state=SaveCodec.decode(SaveCodec.encode(GameEngine.advanceDay(state).state));assertEquals(1,state.realm.level(BuildingType.PALACE)) }
        state=GameEngine.advanceDay(state).state
        assertEquals(2,state.realm.level(BuildingType.PALACE));assertTrue(state.city.constructionQueue.isEmpty())
        assertEquals(state,SaveCodec.decode(SaveCodec.encode(state)))
    }
    @Test fun academyImprovesActualTrainingAndCompanionSyncKeepsHistory() {
        val initial=start().copy(realm=start().realm.copy(buildings=start().realm.buildings+(BuildingType.ACADEMY to 3)))
        val trained=GameEngine.trainCommander(initial,1).state
        assertEquals(initial.commanders.single().leadership+5,trained.commanders.single().leadership)
        assertEquals(initial.commanders.single().tactics+5,trained.commanders.single().tactics)
        var met=RelationshipEngine.syncCommander(trained.copy(companion=trained.companion.copy(met=true)))
        met=met.copy(commanders=met.commanders.map {if(it.id==COMPANION_COMMANDER_ID) it.copy(missionsCompleted=4,battlesFought=3,victories=2,casualties=17) else it})
        val synced=GameEngine.updateCompanionIdentity(met,"Alina Neu",null).commanders.first {it.id==COMPANION_COMMANDER_ID}
        assertEquals(4,synced.missionsCompleted);assertEquals(3,synced.battlesFought);assertEquals(2,synced.victories);assertEquals(17,synced.casualties)
    }
    @Test fun arsenalQuoteEqualsActualDeductionsAndDiscountDoesNotGrantStock() {
        val old=start(); val base=ArmyEngine.equipmentRepairCost(old,UnitType.HUMAN_SWORD)
        val state=old.copy(realm=old.realm.copy(buildings=old.realm.buildings+(BuildingType.ARSENAL to 5)))
        val quote=ArmyEngine.equipmentRepairCost(state,UnitType.HUMAN_SWORD)
        val after=ArmyEngine.repairEquipment(state,UnitType.HUMAN_SWORD).state
        assertTrue(quote.gold<base.gold);assertEquals(quote.gold,state.resources.gold-after.resources.gold)
        assertEquals(quote.iron,state.resources.iron-after.resources.iron)
        assertEquals(100,after.armyPools.first {it.type==UnitType.HUMAN_SWORD}.equipment)
    }
    @Test fun grandfatherStockOverflowShowsOnlyLostTodaysOutput() {
        val old=start();val state=old.copy(resources=old.resources.copy(gold=30000),city=old.city.copy(storageCapacity=old.city.storageCapacity.copy(gold=20000)))
        val detail=EconomyEngine.breakdown(state,ResourceKind.GOLD)
        assertEquals(detail.net,detail.overflow)
        assertEquals(30000,EconomyEngine.day(state).resources.gold)
    }
    @Test fun blockedBattleDayDoesNotConsumeConstructionTimeOrResources() {
        val queued=GameEngine.build(start(),BuildingType.QUARRY).state
        val fighting=BattleEngine.start(queued,EnemyType.ORC,Tactic.HOLD,seed=42,enemyStrength=100).state
        assertEquals(fighting,GameEngine.advanceDay(fighting).state)
    }
}
