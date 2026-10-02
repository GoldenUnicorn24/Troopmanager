package com.goldenunicorn.troopmanager.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.goldenunicorn.troopmanager.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

enum class MusicMood(val asset: String) {
    MENU("menu"), CITY("city"), POLITICS("politics"), WORLD("world"), WAR("war"),
    BATTLE("battle"), CRITICAL("critical"), VICTORY("victory"), DEFEAT("defeat"),
}

object AudioDirector {
    fun mood(state: GameState?, screen: String): MusicMood {
        if (state == null || screen.equals("menu", ignoreCase = true)) return MusicMood.MENU
        val battle = state.battleSession
        if (battle != null && screen.lowercase() in listOf("battle", "schlacht", "live")) {
            return when {
                battle.status == BattleStatus.VICTORY -> MusicMood.VICTORY
                battle.status == BattleStatus.DEFEAT -> MusicMood.DEFEAT
                battle.phase == BattlePhase.CRITICAL || battle.morale < 35 -> MusicMood.CRITICAL
                else -> MusicMood.BATTLE
            }
        }
        if (state.invasion != null || state.realm.threat >= 60 || state.world.factions.any { PLAYER_FACTION in it.wars }) return MusicMood.WAR
        if (screen.lowercase() in listOf("city", "stadt") &&
            (state.society.politicalLoyalty < 35 || state.society.culturalTension >= 65)) return MusicMood.POLITICS
        return when (screen.lowercase()) {
            "world", "welt", "regions", "mission", "missions" -> MusicMood.WORLD
            "court", "hof", "diplomacy", "diplomatie" -> MusicMood.POLITICS
            else -> MusicMood.CITY
        }
    }

    fun culture(state: GameState?): String {
        val p = state?.population ?: return "human"
        return listOf("human" to p.human, "wood" to p.woodElf, "gold" to p.goldElf, "wall" to p.wall)
            .maxBy { it.second }.first
    }

    fun cue(cue: BattleSoundCue): String = when (cue.name) {
        "HORN" -> "horn"; "SWORDS" -> "swords"; "ARROWS" -> "arrows"
        "MONSTERS" -> "monsters"; "WALL_BREAK", "GATE" -> "gates"
        "ARTILLERY" -> "artillery"; "HORSES" -> "horses"; "FIRE" -> "fire"
        else -> "swords"
    }
}

/** Assets are bundled original PCM. No network, account, synthesis or download is needed in play. */
@Composable
fun GameSoundscape(state: GameState?, screen: String) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val haptic = LocalHapticFeedback.current
    val settings = state?.settings ?: GameSettings()
    val player = remember(context) { LocalSoundscape(context) }
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { foreground = true; player.resume() }
                Lifecycle.Event.ON_STOP -> { foreground = false; player.pause() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.close() }
    }
    SideEffect { player.enable(settings.sound && foreground, settings.music && foreground) }
    val mood = AudioDirector.mood(state, screen)
    val culture = AudioDirector.culture(state)
    LaunchedEffect(mood, culture, settings.music, foreground) {
        if (settings.music && foreground) player.transition("audio/music_${mood.asset}_$culture.wav")
        else player.stopMusic()
    }
    val cityScreen = screen.lowercase() in listOf("city", "stadt")
    val weather = state?.world?.weather?.regions?.firstOrNull { it.regionId == "keep" }?.kind
    val ambience = when {
        !cityScreen || state == null -> null
        state.battleSession?.isActive == true -> "fire"
        weather == WeatherKind.RAIN || weather == WeatherKind.STORM -> "rain"
        state.city.workerPriority == WorkerPriority.IRON && state.realm.level(BuildingType.IRONWORKS) > 0 -> "forge"
        state.city.prosperity >= 65 -> "market"
        else -> "city"
    }
    LaunchedEffect(ambience, settings.sound, foreground) {
        player.ambient(if (settings.sound && foreground) ambience else null)
    }
    val battle = state?.battleSession
    val cueKey = battle?.let { "${it.seed}:${it.minute}:${it.status}" }
    LaunchedEffect(cueKey, settings.sound, foreground) {
        if (battle != null && settings.sound && foreground) battle.lastSounds.distinct().take(3).forEach { player.cue(AudioDirector.cue(it)) }
    }
    val armyTravel = state?.world?.armies?.filter { it.factionId == PLAYER_FACTION }
        ?.associate { it.id to it.status } ?: emptyMap()
    var previousArmyTravel by remember { mutableStateOf(armyTravel) }
    LaunchedEffect(armyTravel) {
        if (settings.sound && foreground && armyTravel.any { (id, status) ->
                previousArmyTravel[id] != status && status in listOf(WorldArmyStatus.MARCHING, WorldArmyStatus.HOME)
            }) player.cue("horses")
        previousArmyTravel = armyTravel
    }
    var previousLevel by remember { mutableIntStateOf(state?.player?.level ?: 1) }
    var previousBattle by remember { mutableStateOf(battle?.seed) }
    var previousMinute by remember { mutableIntStateOf(battle?.minute ?: 0) }
    var previousChronicle by remember { mutableStateOf(state?.chronicle?.lastOrNull()) }
    LaunchedEffect(state?.player?.level, cueKey, state?.chronicle?.lastOrNull(), settings.haptics) {
        val level = state?.player?.level ?: 1
        val entry = state?.chronicle?.lastOrNull()
        val newBattle = battle != null && battle.seed != previousBattle
        val gateHit = battle != null && battle.minute > previousMinute && battle.lastSounds.any { it.name == "WALL_BREAK" || it.name == "GATE" }
        val decision = entry != null && entry != previousChronicle &&
            (entry.title.contains("Vertrag", true) || entry.title.contains("Beförder", true) || entry.title.contains("Entscheidung", true))
        if (settings.haptics && foreground && (newBattle || gateHit || level > previousLevel || decision))
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        previousLevel = level; previousBattle = battle?.seed; previousMinute = battle?.minute ?: 0; previousChronicle = entry
    }
}

private class LocalSoundscape(private val context: Context) {
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val pool = SoundPool.Builder().setMaxStreams(5).setAudioAttributes(attributes).build()
    private val ids = ConcurrentHashMap<String, Int>()
    private val loaded = ConcurrentHashMap.newKeySet<Int>()
    private val pending = ConcurrentLinkedQueue<String>()
    @Volatile private var soundEnabled = true
    private var musicEnabled = true
    private var music: MediaPlayer? = null
    private var fading: MediaPlayer? = null
    private var path: String? = null
    private var ambientStream = 0
    private var ambientName: String? = null
    private var focusHeld = false
    private var pausedForFocus = false
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { pausedForFocus = true; pause() }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> music?.setVolume(.06f, .06f)
                AudioManager.AUDIOFOCUS_GAIN -> { pausedForFocus = false; resume(); music?.setVolume(.32f, .32f) }
            }
        }.build()

    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            if (status == 0) {
                loaded.add(id)
                pending.filter { ids[it] == id }.forEach { name -> pending.remove(name); cue(name) }
                if (ambientName?.let { ids[it] } == id && soundEnabled) startAmbient(id)
            }
        }
        listOf("swords", "arrows", "horn", "horses", "artillery", "monsters", "gates", "fire", "city", "market", "forge", "rain").forEach { name ->
            runCatching { context.assets.openFd("audio/cue_$name.wav").use { ids[name] = pool.load(it, 1) } }
        }
    }

    fun enable(sound: Boolean, music: Boolean) {
        soundEnabled = sound; musicEnabled = music
        if (!sound) { pool.autoPause(); pending.clear() }
        if (sound || music) requestFocus()
        else if (focusHeld) { manager.abandonAudioFocusRequest(focus); focusHeld = false }
    }

    private fun requestFocus(): Boolean {
        if (!focusHeld) {
            focusHeld = manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (focusHeld) pausedForFocus = false
        }
        return focusHeld
    }

    fun cue(name: String) {
        if (!soundEnabled || pausedForFocus) return
        val id = ids[name] ?: return
        if (id !in loaded) { if (pending.size < 4) pending.add(name); return }
        pool.play(id, .36f, .36f, 1, 0, 1f)
    }

    fun ambient(name: String?) {
        if (ambientStream != 0) pool.stop(ambientStream)
        ambientStream = 0; ambientName = name
        val id = name?.let { ids[it] } ?: return
        if (id in loaded && soundEnabled) startAmbient(id)
    }

    private fun startAmbient(id: Int) { ambientStream = pool.play(id, .12f, .12f, 0, -1, 1f) }

    suspend fun transition(asset: String) {
        if (!musicEnabled || pausedForFocus || !requestFocus()) return
        if (path == asset && music != null) { if (music?.isPlaying == false) music?.start(); return }
        val next = prepareMusic(asset) ?: return
        next.setVolume(0f, 0f); next.start()
        val previous = music
        fading = previous; music = next; path = asset
        try {
            repeat(30) { step ->
                val volume = (step + 1) / 30f
                next.setVolume(.32f * volume, .32f * volume)
                previous?.setVolume(.32f * (1f - volume), .32f * (1f - volume))
                delay(50)
            }
        } finally {
            previous?.release()
            if (fading === previous) fading = null
        }
    }

    private suspend fun prepareMusic(asset: String): MediaPlayer? = suspendCancellableCoroutine { continuation ->
        val prepared = MediaPlayer()
        continuation.invokeOnCancellation { prepared.release() }
        prepared.setOnPreparedListener {
            if (continuation.isActive) continuation.resume(prepared) else prepared.release()
        }
        prepared.setOnErrorListener { _, _, _ ->
            prepared.release()
            if (continuation.isActive) continuation.resume(null)
            true
        }
        val started = runCatching {
            prepared.setAudioAttributes(attributes)
            context.assets.openFd(asset).use { prepared.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            prepared.isLooping = true; prepared.prepareAsync()
        }.isSuccess
        if (!started) { prepared.release(); if (continuation.isActive) continuation.resume(null) }
    }

    fun stopMusic() {
        music?.release(); music = null; path = null
        // A cancelled transition owns and releases its outgoing player in finally.
    }

    fun pause() { runCatching { music?.pause(); fading?.pause(); pool.autoPause() } }
    fun resume() {
        if (pausedForFocus) return
        if (soundEnabled) pool.autoResume()
        if (musicEnabled) runCatching { music?.start() }
    }
    fun close() {
        soundEnabled = false; musicEnabled = false; pending.clear()
        stopMusic(); pool.release()
        if (focusHeld) manager.abandonAudioFocusRequest(focus)
        focusHeld = false
    }
}
