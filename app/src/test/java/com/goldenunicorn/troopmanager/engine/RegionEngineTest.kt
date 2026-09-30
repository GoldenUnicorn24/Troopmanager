package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class RegionEngineTest {
    private fun game() = GameEngine.newGame("Leon", 23, Species.HUMAN, null)

    @Test
    fun acquisitionPreservesRegionIdentityAndDoesNotChargeTwice() {
        val before = game()
        val after = RegionEngine.buy(before, "mine").state
        assertTrue(after.regions.first { it.id == "mine" }.owned)
        assertEquals(before.regions.size, after.regions.size)
        assertEquals(before.realm.territory + 1, after.realm.territory)
        assertEquals(before.resources.gold - 4000, after.resources.gold)
        assertEquals(before.armyPools, after.armyPools)
        assertEquals(before.commanderAssignments, after.commanderAssignments)
        assertEquals(before.activeMissions, after.activeMissions)
        assertEquals(after, RegionEngine.buy(after, "mine").state)
        assertEquals(after, RegionEngine.negotiate(after, "mine").state)
        assertEquals(after, SaveCodec.decode(SaveCodec.encode(after)))
    }

    @Test
    fun forestAndVillageProvideSpecificImmigrationExactlyOnce() {
        val before = game()
        val forest = RegionEngine.buy(before, "forest").state
        assertEquals(before.population.woodElf + 250, forest.population.woodElf)
        assertEquals(before.population.woodElfRecruits + 40, forest.population.woodElfRecruits)
        assertEquals(before.city.housingCapacity + 250, forest.city.housingCapacity)
        assertEquals(before.population.human, forest.population.human)
        assertEquals(forest, RegionEngine.buy(forest, "forest").state)
        val village = RegionEngine.buy(before, "village").state
        assertEquals(before.population.human + 120, village.population.human)
        assertEquals(before.population.humanRecruits + 20, village.population.humanRecruits)
        assertEquals(before.city.housingCapacity + 120, village.city.housingCapacity)
        assertEquals(before.population.woodElf, village.population.woodElf)
    }

    @Test
    fun invalidTargetsAndUnaffordablePurchasesLeaveEntireStateUnchanged() {
        val before = game()
        listOf("keep", "ruin", "orc", "monsters", "missing").forEach {
            assertEquals(before, RegionEngine.buy(before, it).state)
        }
        val poor = before.copy(resources = before.resources.copy(gold = 3999))
        assertEquals(poor, RegionEngine.buy(poor, "mine").state)
    }

    @Test
    fun diplomaticAcquisitionRequiresSkillAndAppliesVisibleDiscount() {
        val before = game().let { it.copy(player = it.player.copy(diplomacy = 34)) }
        assertEquals(before, RegionEngine.negotiate(before, "mine").state)
        val skilled = before.copy(player = before.player.copy(diplomacy = 60))
        val after = RegionEngine.negotiate(skilled, "mine").state
        assertEquals(skilled.resources.gold - 3200, after.resources.gold)
        assertTrue(after.regions.first { it.id == "mine" }.owned)
        assertEquals(skilled.player.experience + 15, after.player.experience)
    }

    @Test
    fun trustedCompanionContributesOnlyAfterMeetingAndTrustThreshold() {
        val before = game().let { it.copy(player = it.player.copy(diplomacy = 20), companion = it.companion.copy(met = true, trust = 44, diplomacy = 40)) }
        assertEquals(before, RegionEngine.negotiate(before, "mine").state)
        val trusted = before.copy(companion = before.companion.copy(trust = 45))
        assertEquals(40, RegionEngine.diplomacy(trusted))
        assertTrue(RegionEngine.negotiate(trusted, "mine").state.regions.first { it.id == "mine" }.owned)
        val unmet = trusted.copy(companion = trusted.companion.copy(met = false))
        assertEquals(unmet, RegionEngine.negotiate(unmet, "mine").state)
    }
}
