"""Reproduce the original Troopmanager v0.6 PCM sound library with Python 3 stdlib.

All oscillators, rhythmic figures and envelopes below are original, deterministically
generated material. There are no sampled recordings or third-party compositions.
Run: python3 app/src/main/assets/audio/generate_audio.py
"""
from pathlib import Path
import math
import random
import struct
import wave

ROOT = Path(__file__).resolve().parent
RATE = 16000
TAU = math.tau


def write(name, samples):
    peak = max(abs(x) for x in samples) or 1
    gain = min(1, 0.88 / peak)
    payload = b"".join(struct.pack("<h", int(max(-1, min(1, x * gain)) * 32767)) for x in samples)
    with wave.open(str(ROOT / name), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(payload)


def cue(kind, duration):
    rng = random.Random("troopmanager_original_" + kind)
    low = 0.0
    samples = []
    for i in range(int(duration * RATE)):
        t = i / RATE
        n = rng.uniform(-1, 1)
        low = low * .91 + n * .09
        if kind == "swords":
            q = t % .31
            s = (n * .25 + math.sin(TAU * 1769 * t) * .17 + math.sin(TAU * 2391 * t) * .12) * math.exp(-q * 28)
        elif kind == "arrows":
            q = t % .38
            s = n * .18 * math.sin(math.pi * min(1, q / .16)) ** 2 * math.exp(-q * 7)
        elif kind == "horn":
            env = min(1, t / .15) * min(1, (duration - t) / .5)
            s = sum(math.sin(TAU * 146.83 * h * t) / h for h in range(1, 6)) * .19 * env
        elif kind == "horses":
            q = t % .28
            s = (low * .55 + math.sin(TAU * 115 * t) * .12) * math.exp(-q * 55)
        elif kind == "artillery":
            s = (low * .9 + math.sin(TAU * (45 * t + 15 * (1 - math.exp(-t * 6)))) * .35) * math.exp(-t * 2.8)
        elif kind == "monsters":
            s = (math.sin(TAU * (58 * t + 5 * math.sin(t * 4))) * .15 + low * .38) * math.sin(math.pi * t / duration) ** 2
        elif kind == "gates":
            q = t % .62
            s = (low * .7 + math.sin(TAU * 82 * t) * .25 + n * .15) * math.exp(-q * 17)
        elif kind == "fire":
            s = low * .2 + (n * .16 if rng.random() < .008 else 0)
        elif kind == "rain":
            s = n * .11 + low * .13
        elif kind == "forge":
            q = t % .73
            s = (math.sin(TAU * 875 * t) * .15 + math.sin(TAU * 1418 * t) * .1 + n * .1) * math.exp(-q * 22)
        else:
            # Distant speech-like murmurs, footsteps and light market chimes.
            pulse = max(0, math.sin(t * 4.8)) ** 4
            s = low * .11 + math.sin(TAU * (121 * t + 4 * math.sin(t * 2))) * .026 * pulse
            if kind == "market":
                s += math.sin(TAU * 698.46 * t) * .025 * math.exp(-(t % 1.8) * 7)
        fade = min(1, t / .012) * min(1, (duration - t) / .04)
        samples.append(s * fade)
    return samples


MOODS = {
    "menu": (62, .6, [0, 7, 10, 5, 0, 3, 7, 2]),
    "city": (62, .35, [0, 7, 3, 10, 5, 7, 2, 0]),
    "politics": (57, .52, [0, 1, 7, 3, 0, 8, 5, 1]),
    "world": (60, .48, [0, 7, 5, 10, 3, 7, 12, 5]),
    "war": (50, .8, [0, 0, 1, 7, 0, 3, 1, 0]),
    "battle": (50, 1.0, [0, 7, 0, 3, 5, 0, 7, 1]),
    "critical": (49, 1.1, [0, 1, 0, 6, 0, 1, 3, 0]),
    "victory": (62, .7, [0, 4, 7, 12, 9, 7, 4, 12]),
    "defeat": (50, .3, [0, 7, 3, 2, 0, 5, 3, 0]),
}


def music(mood, culture):
    key, energy, melody = MOODS[mood]
    culture_offset = {"human": 0, "wood": 7, "gold": 12, "wall": -5}[culture]
    duration = 8.0
    samples = []
    for i in range(int(duration * RATE)):
        t = i / RATE
        beat = int(t / .5)
        within = t % .5
        note = key + culture_offset + melody[beat % len(melody)]
        f = 440 * 2 ** ((note - 69) / 12)
        env = min(1, within / .025) * math.exp(-within * (7 if culture == "wood" else 3.3))
        if culture == "wood":  # plucked wooden strings and breath
            lead = (math.sin(TAU * f * t) + .32 * math.sin(TAU * f * 2 * t)) * .16 * env
        elif culture == "gold":  # bell-like overtone identity
            lead = (math.sin(TAU * f * t) + .28 * math.sin(TAU * f * 2.76 * t)) * .14 * env
        elif culture == "wall":  # low brass and measured marching pulse
            lead = sum(math.sin(TAU * f * h * t) / h for h in range(1, 4)) * .105 * env
        else:  # bowed string-like harmonic warmth
            lead = (math.sin(TAU * f * t) + .22 * math.sin(TAU * f * 3 * t)) * .15 * env
        bassf = 440 * 2 ** ((key - 24 - 69) / 12)
        pad = (math.sin(TAU * bassf * t) + .3 * math.sin(TAU * bassf * 1.5 * t)) * .08
        drumphase = t % (1 if culture != "wall" else .5)
        drum = math.sin(TAU * (55 * t + 2 * math.sin(t * 15))) * math.exp(-drumphase * 24) * energy * .13
        fade = min(1, t / .1) * min(1, (duration - t) / .16)
        samples.append((lead + pad + drum) * fade)
    return samples


if __name__ == "__main__":
    for kind in ("swords", "arrows", "horn", "horses", "artillery", "monsters", "gates", "fire", "city", "market", "forge", "rain"):
        write("cue_" + kind + ".wav", cue(kind, 4 if kind in ("fire", "city", "market", "rain") else 1.8))
    for mood in MOODS:
        for culture in ("human", "wood", "gold", "wall"):
            write(f"music_{mood}_{culture}.wav", music(mood, culture))
    print("Generated 48 original local PCM assets (16 kHz, mono, signed 16-bit).")
