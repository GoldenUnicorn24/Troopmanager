package com.goldenunicorn.troopmanager.ui

import com.goldenunicorn.troopmanager.model.*
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.engine.BattleEngine
import org.junit.Assert.*
import org.junit.Test

class CitySceneProjectionTest {
    @Test fun liveSiegeUsesActualBattleWallDamageInBothCityScenes() {
        val start=GameEngine.newGame("Test",23,Species.HUMAN,null)
        val fighting=BattleEngine.start(start,EnemyType.ORC,Tactic.FORTIFY,seed=5,enemyStrength=100).state
        val damaged=fighting.copy(battleSession=fighting.battleSession!!.copy(wallIntegrity=42))
        assertEquals(100,damaged.realm.wallIntegrity)
        assertEquals(42,cityWallIntegrity(damaged))
        assertTrue(cityInDefense(damaged))
    }
    @Test fun ordinaryFieldBattleDoesNotReplaceCityWallWithBattleWall() {
        val start=GameEngine.newGame("Test",23,Species.HUMAN,null).copy(realm=Realm(wallIntegrity=87))
        val field=BattleEngine.start(start,EnemyType.ORC,Tactic.HOLD,seed=5,enemyStrength=100).state
        assertEquals(87,cityWallIntegrity(field))
        assertFalse(cityInDefense(field))
    }

    @Test fun transformedBuildingsRemainSelectableAfterPanAndZoom() {
        val projection = CityProjection(800f, 600f, 2.4f, -160f, 70f).bounded()
        val palace = projection.project(735f, 305f)
        assertEquals(BuildingType.PALACE, projection.hit(palace.first, palace.second, 24f))
        val market = projection.project(725f, 620f)
        assertEquals(BuildingType.MARKET, projection.hit(market.first, market.second, 24f))
    }

    @Test fun overlappingAccessibleTargetsStillChooseTappedBuilding() {
        val projection = CityProjection(400f, 700f, 1f, 0f, 0f)
        for (site in citySites) {
            val screen = projection.project(site.x, site.y - 20f)
            assertEquals(site.type, projection.hit(screen.first, screen.second, 72f))
        }
    }

    @Test fun inverseProjectionPreservesCoordinatesForPortraitAndLandscape() {
        for ((width, height) in listOf(400f to 700f, 1200f to 700f)) {
            val projection = CityProjection(width, height, 2f, 90f, -40f).bounded()
            val screen = projection.project(1225f, 440f)
            val restored = projection.unproject(screen.first, screen.second)
            assertEquals(1225f, restored.first, .001f)
            assertEquals(440f, restored.second, .001f)
        }
    }

    @Test fun emptyGroundDoesNotChooseNearestBuilding() {
        val projection = CityProjection(1600f, 1000f, 1f, 0f, 0f)
        assertNull(projection.hit(30f, 30f, 24f))
        assertNull(projection.hit(50f, 950f, 24f))
        assertNull(projection.hit(1570f, 500f, 24f))
    }

    @Test fun boundsPreventLosingCityAndResetPanWhenSceneFits() {
        val zoomed = CityProjection(800f, 1000f, 3f, 9000f, -9000f).bounded()
        assertEquals(800f, zoomed.panX, .001f)
        assertEquals(-250f, zoomed.panY, .001f)
        val overview = CityProjection(800f, 1000f, .1f, 400f, -400f).bounded()
        assertEquals(.85f, overview.zoom, .001f)
        assertEquals(0f, overview.panX, .001f)
        assertEquals(0f, overview.panY, .001f)
        assertEquals(3.5f, zoomed.copy(zoom = 99f).bounded().zoom, .001f)
    }

    @Test fun everyBuildingHasAccessibleDistrictSite() {
        assertEquals(BuildingType.entries.toSet(), citySites.map { it.type }.toSet())
        assertEquals(CityDistrict.entries.toSet(), citySites.map { it.district }.toSet())
    }
}
