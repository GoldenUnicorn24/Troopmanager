"""Original UI cues for v2.1 (fanfare, dawn bell, coin, seal, tap) – deterministic synthesis, stdlib only.
Run: python3 scripts/generate_ui_audio.py
"""
from pathlib import Path
import math, random, struct, wave

OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/audio"
RATE = 22050
TAU = math.tau

def write(name, samples):
    peak = max(abs(x) for x in samples) or 1
    gain = 0.85 / peak
    with wave.open(str(OUT / f"cue_{name}.wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s * gain)) * 32767)) for s in samples))

def brass(freq, t, length):
    if t < 0 or t > length: return 0.0
    env = min(1, t / .04) * (0.75 + .25 * math.exp(-t * 6)) * min(1, (length - t) / .12)
    vib = 1 + .004 * math.sin(TAU * 5.5 * t)
    return sum(math.sin(TAU * freq * h * vib * t) / (h ** 1.15) for h in range(1, 8)) * env

def fanfare():
    notes = [(0.00, 392.0, .18), (0.20, 392.0, .14), (0.36, 523.25, .22), (0.60, 659.25, .22), (0.84, 783.99, .9)]
    dur = 2.0
    out = []
    for i in range(int(dur * RATE)):
        t = i / RATE
        s = sum(brass(f, t - start, l) for start, f, l in notes) * .22
        s += sum(brass(f * .5, t - start, l) for start, f, l in notes[-1:]) * .12
        # timpani roll at the end
        if t > .84: s += math.sin(TAU * 73 * t) * math.exp(-(t - .84) * 3) * .25
        out.append(s * min(1, (dur - t) / .3))
    write("fanfare", out)

def dawn():
    dur = 1.8
    rng = random.Random(7)
    out = []
    for i in range(int(dur * RATE)):
        t = i / RATE
        s = 0.0
        for k, f in enumerate([523.25, 783.99]):
            tt = t - k * .32
            if tt >= 0:
                s += (math.sin(TAU * f * tt) + .5 * math.sin(TAU * f * 2.76 * tt) + .25 * math.sin(TAU * f * 5.4 * tt)) * math.exp(-tt * 2.6)
        out.append(s * .3)
    write("dawn", out)

def coin():
    dur = .45
    out = []
    for i in range(int(dur * RATE)):
        t = i / RATE
        s = 0.0
        for start in (0, .07):
            tt = t - start
            if tt >= 0: s += (math.sin(TAU * 2637 * tt) + .6 * math.sin(TAU * 3951 * tt)) * math.exp(-tt * 18)
        out.append(s * .3)
    write("coin", out)

def seal():
    dur = .35
    rng = random.Random(3); low = 0.0
    out = []
    for i in range(int(dur * RATE)):
        t = i / RATE
        low = low * .85 + rng.uniform(-1, 1) * .15
        out.append((low * .8 + math.sin(TAU * 110 * t) * .5) * math.exp(-t * 22))
    write("seal", out)

def tap():
    dur = .07
    out = [math.sin(TAU * 1400 * (i / RATE)) * math.exp(-(i / RATE) * 70) for i in range(int(dur * RATE))]
    write("tap", out)

for f in (fanfare, dawn, coin, seal, tap): f()
print("ok")
