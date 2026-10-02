package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V061FeaturesTest {
    @Test
    fun newGameHasThreeConstructionSlotsAndThirtySkillPoints() {
        val state = GameEngine.newGame("Leon", 23, Species.HALF_ELF, null)

        assertEquals(3, CityEngine.constructionSlots(state))
        assertEquals(30, state.player.skillPoints)
    }

    @Test
    fun customStartingAttributesAreAppliedExactly() {
        val attributes =
            GameEngine.StartingAttributes(
                sword = 40,
                bow = 35,
                riding = 34,
                leadership = 33,
                tactics = 32,
                diplomacy = 36,
            )
        val state = GameEngine.newGame("Leon", 23, Species.HALF_ELF, null, attributes)

        assertEquals(40, state.player.sword)
        assertEquals(35, state.player.bow)
        assertEquals(34, state.player.riding)
        assertEquals(33, state.player.leadership)
        assertEquals(32, state.player.tactics)
        assertEquals(36, state.player.diplomacy)
        assertEquals(30, state.player.skillPoints)
    }

    @Test
    fun missionSupportsPlayerPlusTwoCommandersAndKeepsPermanentArmyAssignment() {
        var state = GameEngine.newGame("Leon", 23, Species.HUMAN, null)
        state =
            state.copy(
                commanders =
                    state.commanders +
                        Commander(
                            id = 2L,
                            name = "Aurelia",
                            culture = Culture.HUMAN,
                            portraitKey = "knight",
                            leadership = 62,
                            tactics = 61,
                        )
            )

        val permanent =
            ArmyEngine.allocation(
                state,
                commanderId = 1L,
                requested = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 50)),
            ).state
        val before = permanent.commanderAssignments

        val result =
            MissionEngine.start(
                permanent,
                MissionType.PATROL,
                commanderIds = listOf(1L, 2L),
                playerParticipates = true,
                units = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 60)),
            )

        val mission = result.state.activeMissions.single { it.status.isAway }
        assertTrue(mission.playerParticipates)
        assertEquals(listOf(1L, 2L), mission.allCommanderIds)
        assertEquals(before, result.state.commanderAssignments)

        val clamped = ArmyEngine.clampAssignments(result.state)
        assertEquals(before, clamped.commanderAssignments)
        assertTrue(clamped.commanderAway(1L))
        assertTrue(clamped.commanderAway(2L))
    }

    @Test
    fun fourthMissionLeaderIsRejected() {
        var state = GameEngine.newGame("Leon", 23, Species.HUMAN, null)
        state =
            state.copy(
                commanders =
                    state.commanders +
                        listOf(
                            Commander(2L, "A", Culture.HUMAN, "knight"),
                            Commander(3L, "B", Culture.HUMAN, "knight"),
                            Commander(4L, "C", Culture.HUMAN, "knight"),
                        )
            )

        val result =
            MissionEngine.start(
                state,
                MissionType.PATROL,
                commanderIds = listOf(1L, 2L, 3L),
                playerParticipates = true,
                units = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 50)),
            )

        assertTrue(result.state.activeMissions.isEmpty())
        assertTrue(result.message.contains("eine bis drei"))
    }
}
