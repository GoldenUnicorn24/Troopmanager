package com.goldenunicorn.troopmanager.data

import com.goldenunicorn.troopmanager.engine.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SceneAssetLayoutTest {
    private fun layout(name: String) = Json.decodeFromString<SceneAssetLayout>(File("src/main/assets/scenes/$name/layout.json").readText())
    @Test fun all108CellsOfEverySceneAgreeWithTheirActualEngine() {
        listOf(Triple("tavern",TavernEngine.layout,2.2f), Triple("market",MarketEngine.layout,2.4f),
            Triple("barracks",BarracksEngine.layout,2.2f),Triple("castle",CastleEngine.layout,2.6f)).forEach { (name, grid, wall) ->
            val l = layout(name)
            l.validate(grid,wall)
            assertEquals(108, l.cells.size)
            assertEquals((0 until 9).flatMap { y -> (0 until 12).map { x -> x to y } }.toSet(), l.cells.map { it.x to it.y }.toSet())
        }
    }
    @Test(expected = IllegalArgumentException::class) fun changedWalkabilityIsRejected() {
        val l = layout("tavern")
        l.copy(cells = l.cells.map { if (it.x == 6 && it.y == 8) it.copy(walkable = false) else it }).validate(TavernEngine.layout,2.2f)
    }
    @Test(expected = IllegalArgumentException::class) fun missingCellIsRejected() {
        layout("market").let { it.copy(cells = it.cells.dropLast(1)).validate(MarketEngine.layout,2.4f) }
    }
    @Test(expected = IllegalArgumentException::class) fun shiftedCellCenterIsRejected() {
        val l = layout("barracks")
        l.copy(cells = l.cells.map { if (it.x == 6 && it.y == 8) it.copy(centerY = it.centerY + 20f) else it }).validate(BarracksEngine.layout,2.2f)
    }
}
