package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class PresenceEngineTest {
    private fun home(): GameState {
        val state=GameEngine.newGame("Leon",24,Species.HUMAN,null)
        return RelationshipEngine.syncCommander(state.copy(companion=state.companion.copy(met=true)))
    }

    @Test fun missionStatusAndRealParticipantsDetermineLocations() {
        val state=home()
        val mission=ActiveMission(10,MissionType.PATROL,COMPANION_COMMANDER_ID,emptyList(),1,2,0,2,playerParticipates=false)
        val away=state.copy(activeMissions=listOf(mission))
        val p=PresenceEngine.presence(away)
        assertEquals(PresenceLocation.PALACE,p.player.location)
        assertEquals(PresenceLocation.MISSION,p.companion.location)
        assertNotNull(PresenceEngine.sharedActivityBlocker(away))
        assertEquals(PresenceLocation.MISSION,PresenceEngine.presence(away.copy(activeMissions=listOf(mission.copy(playerParticipates=true)))).player.location)
        assertNull(PresenceEngine.sharedActivityBlocker(away.copy(activeMissions=listOf(mission.copy(status=MissionStatus.COMPLETE)))))
    }

    @Test fun fieldArmiesUseRealLeaderAndEngagedStatus() {
        val state=home()
        val army=WorldArmy("companion-field",PLAYER_FACTION,"Alinas Banner",emptyList(),"village",COMPANION_COMMANDER_ID,status=WorldArmyStatus.ENGAGED,arrivalDay=state.day+2)
        val away=state.copy(world=state.world.copy(armies=listOf(army)))
        assertEquals(PresenceLocation.FIELD_ARMY,PresenceEngine.presence(away).companion.location)
        assertEquals("army:companion-field",PresenceEngine.presence(away).companion.bindingId)
        assertEquals(PresenceLocation.PALACE,PresenceEngine.presence(away).player.location)
        assertNotNull(PresenceEngine.sharedActivityBlocker(away))
        assertNull(PresenceEngine.presence(away).companion.returnDay)
        val marching=away.copy(world=away.world.copy(armies=listOf(army.copy(status=WorldArmyStatus.MARCHING,destinationId="village"))))
        val outbound=PresenceEngine.presence(marching).companion
        assertNull(outbound.returnDay)
        assertTrue(outbound.detail.contains("Ankunft Tag ${state.day+2}"))
        assertTrue(outbound.detail.contains(state.world.place("village")!!.name))
        val returning=away.copy(world=away.world.copy(armies=listOf(army.copy(status=WorldArmyStatus.RETURNING,destinationId="keep"))))
        assertEquals(state.day+2,PresenceEngine.presence(returning).companion.returnDay)
    }

    @Test fun hospitalVisitAllowsConsciousWoundedCompanionButNoRomance() {
        val state=home().copy(war=home().war.copy(commanderConditions=listOf(CommanderCondition(COMPANION_COMMANDER_ID,CombatantStatus.WOUNDED,9))))
        assertEquals(PresenceLocation.HOSPITAL,PresenceEngine.presence(state).companion.location)
        assertNotNull(PresenceEngine.sharedActivityBlocker(state))
        assertNull(PresenceEngine.sharedActivityBlocker(state,true))
        val unconscious=state.copy(war=state.war.copy(commanderConditions=listOf(CommanderCondition(COMPANION_COMMANDER_ID,CombatantStatus.UNCONSCIOUS,9))))
        assertNotNull(PresenceEngine.sharedActivityBlocker(unconscious,true))
        val captive=state.copy(war=state.war.copy(commanderConditions=listOf(CommanderCondition(COMPANION_COMMANDER_ID,CombatantStatus.CAPTURED))))
        assertNotNull(PresenceEngine.sharedActivityBlocker(captive,true))
    }

    @Test fun healthOutranksSavedVisitsAndLocalVisitsNeedSamePlace() {
        val state=home()
        val visit=PresenceEngine.visit(state,PresenceLocation.WALL).state
        assertEquals(PresenceLocation.WALL,PresenceEngine.presence(visit).player.location)
        assertNotNull(PresenceEngine.sharedActivityBlocker(visit))
        assertEquals(visit,PresenceEngine.visit(visit,PresenceLocation.CITY,true).state)
        val wounded=visit.copy(war=visit.war.copy(playerCondition=CombatantStatus.WOUNDED,playerRecoveryDay=8))
        assertEquals(PresenceLocation.HOSPITAL,PresenceEngine.presence(wounded).player.location)
        assertEquals(wounded,PresenceEngine.visit(wounded,PresenceLocation.PALACE).state)
    }

    @Test fun travelReturnsOnScheduledDayWithoutClearingMilitaryBindings() {
        val state=home()
        val trip=PresenceEngine.travel(state,true,3).state
        assertEquals(state.resources.gold-120,trip.resources.gold)
        assertEquals(PresenceLocation.TRAVEL,PresenceEngine.presence(trip).player.location)
        assertEquals(PresenceLocation.PALACE,PresenceEngine.presence(trip.copy(day=state.day+3)).player.location)
        val mission=ActiveMission(10,MissionType.PATROL,COMPANION_COMMANDER_ID,emptyList(),1,2,0,2,playerParticipates=true)
        val assigned=trip.copy(activeMissions=listOf(mission))
        assertEquals(PresenceLocation.MISSION,PresenceEngine.presence(assigned).player.location)
        assertEquals(assigned,PresenceEngine.travel(assigned).state)
    }

    @Test fun manualTravelBlocksDispatchAndMissionForEachTravellingRuler() {
        val initial=home()
        val travelling=PresenceEngine.travel(initial,true,3).state
        assertEquals(PresenceLocation.TRAVEL,PresenceEngine.presence(travelling).player.location)
        assertEquals(PresenceLocation.TRAVEL,PresenceEngine.presence(travelling).companion.location)
        val units=listOf(UnitAllocation(UnitType.HUMAN_SWORD,60))
        val rulerDispatch=WorldEngine.dispatch(travelling,"village",null,units)
        val companionDispatch=WorldEngine.dispatch(travelling,"village",COMPANION_COMMANDER_ID,units)
        val rulerMission=MissionEngine.start(travelling,MissionType.PATROL,emptyList(),true,units)
        val companionMission=MissionEngine.start(travelling,MissionType.PATROL,listOf(COMPANION_COMMANDER_ID),false,units)
        listOf(rulerDispatch,companionDispatch,rulerMission,companionMission).forEach { result ->
            assertEquals(travelling,result.state)
        }
        assertTrue(rulerDispatch.message.contains("gebunden"))
        assertTrue(rulerMission.message.contains("gebunden"))
        assertTrue(companionMission.message.contains("nicht verfügbar"))
    }

    @Test fun personalFieldArmyPreventsSecondPersonalExpeditionAndMission() {
        val initial=home()
        val units=listOf(UnitAllocation(UnitType.HUMAN_SWORD,60))
        val deployed=WorldEngine.dispatch(initial,"village",null,units).state
        assertEquals(1,deployed.world.playerFieldArmies.size)
        assertEquals(PresenceLocation.FIELD_ARMY,PresenceEngine.presence(deployed).player.location)
        val second=WorldEngine.dispatch(deployed,"village",null,units)
        val mission=MissionEngine.start(deployed,MissionType.PATROL,emptyList(),true,units)
        assertEquals(deployed,second.state)
        assertEquals(deployed,mission.state)
        assertTrue(second.message.contains("gebunden"))
        assertTrue(mission.message.contains("gebunden"))
        assertEquals(deployed.resources,second.state.resources)
        assertEquals(deployed.armyPools,mission.state.armyPools)
    }
}
