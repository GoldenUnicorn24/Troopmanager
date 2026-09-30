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

public val Icons.Outlined.Construction: ImageVector
    get() {
        if (_construction != null) {
            return _construction!!
        }
        _construction = materialIcon(name = "Outlined.Construction") {
            materialPath {
                moveTo(13.783f, 15.172f)
                lineToRelative(2.121f, -2.121f)
                lineToRelative(5.996f, 5.996f)
                lineToRelative(-2.121f, 2.121f)
                close()
            }
            materialPath {
                moveTo(17.5f, 10.0f)
                curveToRelative(1.93f, 0.0f, 3.5f, -1.57f, 3.5f, -3.5f)
                curveToRelative(0.0f, -0.58f, -0.16f, -1.12f, -0.41f, -1.6f)
                lineToRelative(-2.7f, 2.7f)
                lineTo(16.4f, 6.11f)
                lineToRelative(2.7f, -2.7f)
                curveTo(18.62f, 3.16f, 18.08f, 3.0f, 17.5f, 3.0f)
                curveTo(15.57f, 3.0f, 14.0f, 4.57f, 14.0f, 6.5f)
                curveToRelative(0.0f, 0.41f, 0.08f, 0.8f, 0.21f, 1.16f)
                lineToRelative(-1.85f, 1.85f)
                lineToRelative(-1.78f, -1.78f)
                lineToRelative(0.71f, -0.71f)
                lineTo(9.88f, 5.61f)
                lineTo(12.0f, 3.49f)
                curveToRelative(-1.17f, -1.17f, -3.07f, -1.17f, -4.24f, 0.0f)
                lineTo(4.22f, 7.03f)
                lineToRelative(1.41f, 1.41f)
                horizontalLineTo(2.81f)
                lineTo(2.1f, 9.15f)
                lineToRelative(3.54f, 3.54f)
                lineToRelative(0.71f, -0.71f)
                verticalLineTo(9.15f)
                lineToRelative(1.41f, 1.41f)
                lineToRelative(0.71f, -0.71f)
                lineToRelative(1.78f, 1.78f)
                lineToRelative(-7.41f, 7.41f)
                lineToRelative(2.12f, 2.12f)
                lineTo(16.34f, 9.79f)
                curveTo(16.7f, 9.92f, 17.09f, 10.0f, 17.5f, 10.0f)
                close()
            }
        }
        return _construction!!
    }

private var _construction: ImageVector? = null
