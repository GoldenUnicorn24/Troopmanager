package com.goldenunicorn.troopmanager.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector

/** Original crown pictogram for the v1.1 crown area (24 dp grid, outlined style). */
public val Icons.Outlined.Crown: ImageVector
    get() {
        if (_crown != null) return _crown!!
        _crown = materialIcon(name = "Outlined.Crown") {
            materialPath(pathFillType = PathFillType.EvenOdd) {
                // Outer crown with three points.
                moveTo(3.0f, 6.0f)
                lineTo(7.5f, 10.5f)
                lineTo(12.0f, 4.0f)
                lineTo(16.5f, 10.5f)
                lineTo(21.0f, 6.0f)
                lineTo(19.0f, 17.0f)
                lineTo(5.0f, 17.0f)
                close()
                // Inner cut-out keeps the outlined look.
                moveTo(6.7f, 15.0f)
                lineTo(17.3f, 15.0f)
                lineTo(18.2f, 10.2f)
                lineTo(16.2f, 12.9f)
                lineTo(12.0f, 7.4f)
                lineTo(7.8f, 12.9f)
                lineTo(5.8f, 10.2f)
                close()
                // Base band.
                moveTo(5.0f, 18.5f)
                lineTo(19.0f, 18.5f)
                lineTo(19.0f, 20.5f)
                lineTo(5.0f, 20.5f)
                close()
            }
        }
        return _crown!!
    }

private var _crown: ImageVector? = null
