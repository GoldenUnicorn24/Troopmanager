package com.goldenunicorn.troopmanager.ui

import com.goldenunicorn.troopmanager.model.AppearanceOptions
import org.junit.Assert.*
import org.junit.Test

/** Regression for floating weapons: every weapon is attached to the body, none stands beside it. */
class WeaponCarryTest {
    @Test fun everyWeaponHasACarryRig() {
        AppearanceOptions.weapons.indices.forEach { assertTrue("Waffe $it", WeaponCarry.parts[it].orEmpty().isNotEmpty()) }
    }
    @Test fun partsAreCutFromTheSheetAndAnchoredOnTheTorso() {
        WeaponCarry.parts.values.flatten().forEach { part ->
            val (x0, y0, x1, y1) = part.src.toList()
            assertTrue(x0 in 0 until x1 && x1 <= 768 && y0 in 0 until y1 && y1 <= 1536)
            assertTrue("Drehpunkt im Ausschnitt", part.pivot.x in x0.toFloat()..x1.toFloat() && part.pivot.y in y0.toFloat()..y1.toFloat())
            // Anchors lie on the torso between the shoulders (y≈300) and the hips (y≈720), near the body axis.
            assertTrue("Anker am Körper: ${part.target}", part.target.x in 260f..520f && part.target.y in 300f..720f)
            assertTrue(part.scale in .4f..1.1f && kotlin.math.abs(part.angle) <= 30f)
        }
    }
    @Test fun seatedTorsoIsLower() {
        assertEquals(0f, WeaponCarry.torsoOffset("standing"), 0f)
        assertEquals(360f, WeaponCarry.torsoOffset("sitting"), 0f)
    }
}
