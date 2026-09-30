/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.goldenunicorn.troopmanager.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Outlined.Forest: ImageVector
    get() {
        if (_forest != null) {
            return _forest!!
        }
        _forest = materialIcon(name = "Outlined.Forest") {
            materialPath {
                moveTo(24.0f, 18.0f)
                lineToRelative(-3.86f, -6.0f)
                horizontalLineTo(22.0f)
                lineTo(15.0f, 2.0f)
                lineToRelative(-3.0f, 4.29f)
                lineTo(9.0f, 2.0f)
                lineTo(2.0f, 12.0f)
                horizontalLineToRelative(1.86f)
                lineTo(0.0f, 18.0f)
                horizontalLineToRelative(7.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineTo(24.0f)
                close()
                moveTo(15.0f, 5.49f)
                lineTo(18.16f, 10.0f)
                horizontalLineToRelative(-1.68f)
                lineToRelative(3.86f, 6.0f)
                horizontalLineToRelative(-3.62f)
                lineToRelative(-2.57f, -4.0f)
                horizontalLineTo(16.0f)
                lineToRelative(-2.78f, -3.97f)
                lineTo(15.0f, 5.49f)
                close()
                moveTo(3.66f, 16.0f)
                lineToRelative(3.86f, -6.0f)
                horizontalLineTo(5.84f)
                lineTo(9.0f, 5.49f)
                lineTo(12.16f, 10.0f)
                horizontalLineToRelative(-1.68f)
                lineToRelative(3.86f, 6.0f)
                horizontalLineTo(3.66f)
                close()
            }
        }
        return _forest!!
    }

private var _forest: ImageVector? = null
