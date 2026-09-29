package com.goldenunicorn.troopmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.goldenunicorn.troopmanager.data.SaveRepository
import com.goldenunicorn.troopmanager.ui.RealmGameApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saves = SaveRepository(this)
        setContent {
            RealmGameApp(saves)
        }
    }
}
