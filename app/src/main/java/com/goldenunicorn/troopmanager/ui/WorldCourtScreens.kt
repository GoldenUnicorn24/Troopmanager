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
    var battleReport by remember { mutableStateOf<String?>(null) }

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
                        Text("Gold, Nahrung, Ruhm und gemeinsame Erfahrung", color = Mist, fontSize = 12.sp)
                    }
                    Text("›", color = Gold, fontSize = 28.sp)
                }
            }
        }

        item { TacticalBattlePreview(state, enemy, tactic) }
        item { SectionTitle("Große Schlacht") }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Feind", color = PaleGold, fontWeight = FontWeight.Bold)
                    EnemyType.entries.forEach { option ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            RadioButton(selected = option == enemy, onClick = { enemy = option })
                            Text(option.label, color = Color.White, modifier = Modifier.padding(top = 12.dp))
                        }
                    }

                    HorizontalDivider(color = Color(0xFF303942), modifier = Modifier.padding(vertical = 8.dp))
                    Text("Taktik", color = PaleGold, fontWeight = FontWeight.Bold)

                    Tactic.entries.forEach { option ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            RadioButton(selected = option == tactic, onClick = { tactic = option })
                            Text(option.label, color = Color.White, modifier = Modifier.padding(top = 12.dp), fontSize = 13.sp)
                        }
                    }

                    GoldButton(
                        "Schlacht beginnen",
                        {
                            val result = GameEngine.simulateBattle(state, enemy, tactic)
                            onState(result.state)
                            battleReport = result.headline + "\n" + result.details
                        },
                        Modifier.fillMaxWidth().padding(top = 10.dp)
                    )
                }
            }
        }

        battleReport?.let { report ->
            item {
                val won = report.startsWith("Sieg")
                Surface(
                    color = if (won) Color(0xFF183024) else Color(0xFF331E1E),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (won) Success else Danger)
                ) {
                    Text(report, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
                }
            }
        }

        item {
            Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Wie die Schlacht berechnet wird", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        "Regimentstyp, Mannstärke, Erfahrung, Moral, Kommandanten, deine Gefährtin, Taktik sowie Mauern wirken gemeinsam. Goldelben sind mächtig, aber schwer zu ersetzen; Monster verursachen die höchsten Verluste.",
                        color = Mist,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
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
