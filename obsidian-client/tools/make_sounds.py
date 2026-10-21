"""Synthesises the menu button sounds (hover tick and click chime).

Run from obsidian-client/: python3 tools/make_sounds.py   (needs numpy and ffmpeg)
"""
import subprocess
import wave

import numpy as np

RATE = 44100
OUT = "src/main/resources/assets/obsidian/sounds/"


def tone(freq, length, decay, attack=0.004):
    t = np.arange(int(RATE * length)) / RATE
    env = np.minimum(1, t / attack) * np.exp(-t / decay)
    # A soft bell: fundamental plus a quiet octave.
    return (np.sin(2 * np.pi * freq * t) + 0.25 * np.sin(4 * np.pi * freq * t)) * env


def write(name, samples, gain):
    samples = samples / np.max(np.abs(samples)) * gain
    pcm = (samples * 32767).astype(np.int16)
    wav = f"/tmp/{name}.wav"
    with wave.open(wav, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav, "-c:a", "libvorbis", "-q:a", "5", OUT + name + ".ogg"], check=True)


# Hover: a very short, quiet, high tick.
hover = tone(2100, 0.06, 0.012) * 0.7 + tone(1400, 0.06, 0.018) * 0.3
write("hover", hover, 0.35)

# Click: two quick rising notes, like a soft "ding-ding".
a = tone(880, 0.28, 0.07)
b = tone(1318.5, 0.32, 0.09)
click = np.zeros(int(RATE * 0.36))
click[:len(a)] += a
start = int(RATE * 0.055)
click[start:start + len(b)] += b[: len(click) - start]
# A tiny low thump under the first note gives it some weight.
thump_t = np.arange(int(RATE * 0.05)) / RATE
click[: len(thump_t)] += np.sin(2 * np.pi * 140 * thump_t) * np.exp(-thump_t / 0.012) * 0.6
write("click", click, 0.6)
print("ok")
