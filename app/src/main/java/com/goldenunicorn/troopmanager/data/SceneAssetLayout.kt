package com.goldenunicorn.troopmanager.data

import com.goldenunicorn.troopmanager.engine.TavernEngine
import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
data class SceneCell(val x: Int, val y: Int, val centerX: Float, val centerY: Float, val walkable: Boolean)
@Serializable
data class SceneForeground(val row: Int, val path: String)
@Serializable
data class SceneAssetLayout(
    val version: Int,
    val width: Int,
    val height: Int,
    val columns: Int,
    val rows: Int,
    val wallTiles: Float,
    val cellRatio: Float,
    val image: String,
    val floorPolygon: List<List<Float>>,
    val cells: List<SceneCell>,
    val foreground: List<SceneForeground>,
) {
    fun validate(grid: TavernEngine.WalkGrid, expectedWallTiles: Float) {
        require(version == 1 && width == 2160 && height == 1440) { "Ungültiger Szenenstandard." }
        require(columns == grid.width && rows == grid.height && columns == 12 && rows == 9) { "Das Szenenraster stimmt nicht mit der Engine überein." }
        require(abs(wallTiles - expectedWallTiles) < .001f && abs(cellRatio - .66f) < .001f) { "Die Szenenprojektion stimmt nicht mit der Ansicht überein." }
        require(image == "day.webp" && foreground.sortedBy { it.row } == (0 until rows).map { SceneForeground(it, "foreground_$it.webp") }) { "Ungültige Verdeckungsebenen." }
        val factor = height / (wallTiles + rows * cellRatio)
        val floor = wallTiles * factor
        val expectedPolygon = listOf(listOf(0f, floor), listOf(width.toFloat(), floor), listOf(width.toFloat(), height.toFloat()), listOf(0f, height.toFloat()))
        require(floorPolygon.size == 4 && floorPolygon.zip(expectedPolygon).all { (actual, expected) -> actual.size == 2 && actual.zip(expected).all { (a, b) -> a.isFinite() && abs(a - b) < .1f } }) { "Ungültiges Bodenpolygon." }
        require(cells.size == columns * rows && cells.map { it.x to it.y }.toSet().size == cells.size) { "Fehlende oder doppelte Szenenzellen." }
        cells.forEach { c ->
            require(c.x in 0 until columns && c.y in 0 until rows)
            require(c.walkable == grid.walkable(TavernEngine.Cell(c.x, c.y))) { "Szenenzelle ${c.x},${c.y} stimmt nicht mit der Engine überein." }
            require(c.centerX.isFinite() && c.centerY.isFinite() &&
                abs(c.centerX - (c.x + .5f) * width / columns) < .1f &&
                abs(c.centerY - (wallTiles + (c.y + .5f) * cellRatio) * factor) < .1f) { "Falscher Zellmittelpunkt." }
        }
    }
}
