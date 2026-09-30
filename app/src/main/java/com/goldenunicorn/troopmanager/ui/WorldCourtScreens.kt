package com.goldenunicorn.troopmanager.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.R
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.engine.customizeCompanion
import com.goldenunicorn.troopmanager.engine.customizePlayer
import com.goldenunicorn.troopmanager.model.*

@Composable
internal fun WorldScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    var enemy by remember { mutableStateOf(EnemyType.ORC) }
    var tactic by remember { mutableStateOf(Tactic.HOLD) }
    var liveBattle by remember { mutableStateOf<GameEngine.LiveBattleResult?>(null) }

    liveBattle?.let { result ->
        LiveBattleScreen(
            stateBeforeBattle = state,
            enemy = enemy,
            tactic = tactic,
            result = result,
            onApplyResult = {
                onState(it)
                liveBattle = null
                onNotice(if (result.victory) "Schlacht gewonnen." else "Schlacht beendet.")
            },
            onCancel = { liveBattle = null }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PageTitle("WELT & KRIEG", "Ruhm " + state.renown + " · Bedrohung " + state.realm.threat + "%") }
        item {
            Image(
                painterResource(R.drawable.art_world_map),
                null,
                Modifier.fillMaxWidth().height(190.dp),
                contentScale = ContentScale.Crop
            )
        }

        item { SectionTitle("Deine aktuelle Streitmacht") }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(17.dp)) {
                Column(Modifier.padding(15.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Gesamtstärke", color = Mist)
                        Text(state.armySize.toString() + " Soldaten", color = PaleGold, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(8.dp))
                    Culture.entries.forEach { culture ->
                        val count = UnitType.entries.filter { it.culture == culture }.sumOf { state.soldiers(it) }
                        if (count > 0) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(culture.label, color = Color.White, fontSize = 12.sp)
                                Text(count.toString(), color = Mist, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        item { SectionTitle("Persönliche Missionen") }
        items(MissionType.entries) { mission ->
            Surface(
                onClick = {
                    val result = GameEngine.runMission(state, mission)
                    onState(result.state)
                    onNotice(result.message)
                },
                color = Panel,
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(mission.label, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Gold, Nahrung und Ruhm", color = Mist, fontSize = 11.sp)
                    }
                    Text("›", color = Gold, fontSize = 28.sp)
                }
            }
        }

        item { SectionTitle("Große Schlacht vorbereiten") }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Gegner wählen", color = PaleGold, fontWeight = FontWeight.Bold)
                    EnemyType.entries.forEach { option ->
                        val selected = enemy == option
                        Surface(
                            onClick = { enemy = option },
                            color = if (selected) Color(0xFF25313A) else Color(0xFF171F26),
                            shape = RoundedCornerShape(12.dp),
                            border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Danger) else null
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(option.label, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(enemyDescription(option), color = Mist, fontSize = 10.sp)
                                }
                                if (selected) Text("AUSGEWÄHLT", color = Danger, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("2. Taktik wählen", color = PaleGold, fontWeight = FontWeight.Bold)
                    Tactic.entries.forEach { option ->
                        val selected = tactic == option
                        Surface(
                            onClick = { tactic = option },
                            color = if (selected) Color(0xFF25313A) else Color(0xFF171F26),
                            shape = RoundedCornerShape(12.dp),
                            border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Gold) else null
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(option.label, color = Color.White, fontWeight = FontWeight.Bold)
                                    if (selected) Text("AKTIV", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(tacticDescription(option), color = Mist, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }

                    Surface(color = Color(0xFF11181E), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Was passiert danach?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(
                                "Die Schlacht läuft jetzt in einer echten Live-Ansicht über mehrere Phasen bis Minute 90. Du siehst Frontverlauf, aktuelle Mannstärke, Verluste und Ereignisse und kannst pausieren oder die Geschwindigkeit ändern.",
                                color = Mist,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    GoldButton(
                        "Live-Schlacht starten",
                        {
                            if (state.armySize <= 0) {
                                onNotice("Du hast noch keine einsatzbereiten Soldaten.")
                            } else {
                                liveBattle = GameEngine.simulateBattleLive(state, enemy, tactic)
                            }
                        },
                        Modifier.fillMaxWidth()
                    )
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

private fun enemyDescription(enemy: EnemyType): String = when (enemy) {
    EnemyType.ORC -> "Viele schwächere Gegner. Gute erste große Schlacht."
    EnemyType.URUK -> "Schwer gerüstet, diszipliniert und gefährlich im Nahkampf."
    EnemyType.TAO_TEI -> "Schneller Schwarm, hohe Verluste möglich. Fernkampf und Mauern helfen."
}

private fun tacticDescription(tactic: Tactic): String = when (tactic) {
    Tactic.HOLD -> "Ausgewogen. Die Linie bleibt stabil und wartet auf Fehler des Gegners."
    Tactic.AGGRESSIVE -> "Mehr Druck und Tempo. Stark gegen Orks, riskanter gegen schwere Gegner."
    Tactic.RANGED -> "Bogenschützen und Artillerie erhalten mehr Einfluss auf den Schlachtverlauf."
    Tactic.FLANK -> "Versucht den Gegner seitlich zu brechen. Besonders wirksam gegen starre Kriegsheere."
    Tactic.FORTIFY -> "Nutzt Mauern und Türme. Sehr stark bei gut ausgebauter Festungsverteidigung."
}

@Composable
internal fun CourtScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    val context = LocalContext.current

    var playerName by remember(state.player.name) { mutableStateOf(state.player.name) }
    var playerAge by remember(state.player.age) { mutableStateOf(state.player.age.toString()) }
    var playerArmor by remember(state.player.armorStyle) { mutableStateOf(state.player.armorStyle) }
    var playerWeapon by remember(state.player.weapon) { mutableStateOf(state.player.weapon) }

    var companionName by remember(state.companion.name) { mutableStateOf(state.companion.name) }
    var companionAge by remember(state.companion.age) { mutableStateOf(state.companion.age.toString()) }
    var companionArmor by remember(state.companion.armorStyle) { mutableStateOf(state.companion.armorStyle) }
    var companionWeapon by remember(state.companion.weapon) { mutableStateOf(state.companion.weapon) }

    val playerPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onState(GameEngine.updatePlayerPortrait(state, uri.toString()))
        }
    }
    val companionPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onState(GameEngine.updateCompanionIdentity(state, companionName, uri.toString()))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PageTitle("HOF & CHARAKTERE", "Spieler, Gefährtin und Kommandanten sind die individuellen Figuren deines Reiches.") }

        item {
            CharacterPanel(
                title = state.player.name,
                subtitle = state.player.species.label + " · " + state.rank + " · " + state.title,
                uri = state.player.portraitUri,
                fallback = R.drawable.portrait_knight,
                stats = listOf(
                    "Schwert " + state.player.sword,
                    "Bogen " + state.player.bow,
                    "Führung " + state.player.leadership,
                    "Taktik " + state.player.tactics
                ),
                onPortrait = { playerPicker.launch(arrayOf("image/*")) }
            )
        }

        item {
            Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Spieler vollständig anpassen", color = PaleGold, fontWeight = FontWeight.Bold)
                    OutlinedTextField(playerName, { playerName = it.take(24) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(playerAge, { playerAge = it.filter(Char::isDigit).take(3) }, label = { Text("Alter") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(playerArmor, { playerArmor = it.take(36) }, label = { Text("Rüstung / Stil") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(playerWeapon, { playerWeapon = it.take(36) }, label = { Text("Hauptwaffe") }, modifier = Modifier.fillMaxWidth())
                    SmallAction("Spieler übernehmen") {
                        onState(
                            customizePlayer(
                                state,
                                playerName,
                                playerAge.toIntOrNull() ?: state.player.age,
                                playerArmor,
                                playerWeapon
                            )
                        )
                        onNotice("Spieler angepasst.")
                    }
                }
            }
        }

        item { SectionTitle("Gefährtin & spätere Mitregentin") }
        if (!state.companion.met) {
            item {
                EmptyCard("Ihr seid euch noch nicht begegnet. Die Soldatin schließt sich im frühen Spiel nach einigen Tagen organisch an.")
            }
        } else {
            item {
                CharacterPanel(
                    title = state.companion.name,
                    subtitle = state.companion.role + " · Stufe " + state.companion.level,
                    uri = state.companion.portraitUri,
                    fallback = R.drawable.portrait_companion,
                    stats = listOf(
                        "Schwert " + state.companion.sword,
                        "Bogen " + state.companion.bow,
                        "Führung " + state.companion.leadership,
                        "Diplomatie " + state.companion.diplomacy
                    ),
                    onPortrait = { companionPicker.launch(arrayOf("image/*")) }
                )
            }

            item {
                Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Gefährtin vollständig anpassen", color = PaleGold, fontWeight = FontWeight.Bold)
                        OutlinedTextField(companionName, { companionName = it.take(24) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(companionAge, { companionAge = it.filter(Char::isDigit).take(3) }, label = { Text("Alter") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(companionArmor, { companionArmor = it.take(36) }, label = { Text("Rüstung / Stil") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(companionWeapon, { companionWeapon = it.take(36) }, label = { Text("Hauptwaffe") }, modifier = Modifier.fillMaxWidth())
                        SmallAction("Gefährtin übernehmen") {
                            onState(
                                customizeCompanion(
                                    state,
                                    companionName,
                                    companionAge.toIntOrNull() ?: state.companion.age,
                                    companionArmor,
                                    companionWeapon
                                )
                            )
                            onNotice("Gefährtin angepasst.")
                        }
                    }
                }
            }

            item {
                val stage = relationshipStage(state.companion)
                StatGrid(
                    listOf(
                        "Vertrauen" to (state.companion.trust.toString() + "%"),
                        "Respekt" to (state.companion.respect.toString() + "%"),
                        "Zuneigung" to (state.companion.affection.toString() + "%"),
                        "Beziehung" to stage
                    )
                )
            }
            item {
                Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RelationshipAction("Gemeinsam sprechen", state, "talk", onState, onNotice)
                        RelationshipAction("Gemeinsam trainieren", state, "train", onState, onNotice)
                        RelationshipAction("Eigenes Kommando übertragen", state, "command", onState, onNotice)
                        RelationshipAction("Gemeinsam das Reich leiten", state, "court", onState, onNotice)
                    }
                }
            }
        }
    }
}

private fun relationshipStage(companion: CompanionProfile): String = when {
    companion.affection >= 80 && companion.trust >= 80 && companion.respect >= 70 -> "Herrscherpaar"
    companion.affection >= 60 && companion.trust >= 60 -> "Beziehung"
    companion.trust >= 45 && companion.respect >= 45 -> "Enge Gefährten"
    companion.trust >= 30 -> "Freunde"
    else -> "Gefährten"
}

@Composable
private fun RelationshipAction(
    label: String,
    state: GameState,
    action: String,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit
) {
    SmallAction(label) {
        val result = GameEngine.companionAction(state, action)
        onState(result.state)
        onNotice(result.message)
    }
}
