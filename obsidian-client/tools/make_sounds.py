"""Synthesises soft, high, calm button sounds: a click and a matching hover for each style.

Run from obsidian-client/: python3 tools/make_sounds.py OUT_DIR   (needs numpy and ffmpeg)
Writes <style>_click.ogg and <style>_hover.ogg for every style below.
"""
import os
import subprocess
import sys
import wave

import numpy as np

RATE = 44100
rng = np.random.default_rng(1)


def t_axis(length):
    return np.arange(int(RATE * length)) / RATE


def env(length, attack, decay):
    t = t_axis(length)
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    a = a * a * (3 - 2 * a)  # smooth start, no click
    e = a * np.exp(-t / decay)
    fade = np.clip((length - t) / 0.01, 0, 1)  # always end at silence
    return e * fade


def sine(freq, length, phase=0.0):
    """freq may be a number or an array (for pitch sweeps)."""
    t = t_axis(length)
    f = np.broadcast_to(np.asarray(freq, dtype=float), t.shape)
    return np.sin(2 * np.pi * np.cumsum(f) / RATE + phase)


def sweep(f0, f1, length, time):
    t = t_axis(length)
    k = np.clip(t / time, 0, 1)
    return f0 * (f1 / f0) ** k


def partials(freqs, amps, decays, length, attack=0.003):
    out = np.zeros(int(RATE * length))
    for f, a, d in zip(freqs, amps, decays):
        out += a * sine(f, length) * env(length, attack, d)
    return out


def room(x, amount=0.12, tail=0.16):
    """A hint of space: convolve with a short, soft, decaying noise burst."""
    n = int(RATE * tail)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / (RATE * tail / 5))
    ir = np.convolve(ir, np.ones(24) / 24, mode="same")  # darken the tail
    ir /= np.abs(ir).sum()
    wet = np.convolve(x, ir)
    dry = np.concatenate([x, np.zeros(len(wet) - len(x))])
    return dry + amount * wet / (np.abs(wet).max() + 1e-9) * np.abs(x).max()


def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / RATE)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def noise_ping(center, length, decay, q=30):
    """Filtered noise ringing at one pitch: woody or airy ticks."""
    n = rng.standard_normal(int(RATE * length)) * env(length, 0.0005, 0.004)
    # Two-pole resonator.
    r = np.exp(-np.pi * center / (q * RATE))
    c = 2 * r * np.cos(2 * np.pi * center / RATE)
    y = np.zeros_like(n)
    for i in range(len(n)):
        y[i] = n[i] + c * (y[i - 1] if i else 0) - r * r * (y[i - 2] if i > 1 else 0)
    return y * env(length, 0.0005, decay)


STYLES = {
    "water": (
        lambda: sine(sweep(750, 1450, 0.16, 0.035), 0.16) * env(0.16, 0.002, 0.045),
        lambda: sine(sweep(1150, 1750, 0.08, 0.02), 0.08) * env(0.08, 0.002, 0.02),
    ),
    "marimba": (
        lambda: partials([1175, 4700, 3170], [1, 0.12, 0.05], [0.08, 0.015, 0.01], 0.3),
        lambda: partials([1568, 6272], [1, 0.08], [0.025, 0.008], 0.1),
    ),
    "glass": (
        lambda: partials([2093, 5776, 11300], [1, 0.25, 0.08], [0.09, 0.04, 0.015], 0.3, 0.001),
        lambda: partials([2637, 7280], [1, 0.15], [0.03, 0.012], 0.1, 0.001),
    ),
    "kalimba": (
        lambda: partials([880, 1760, 4752], [1, 0.18, 0.12], [0.15, 0.05, 0.012], 0.4, 0.002),
        lambda: partials([1320, 7128], [1, 0.08], [0.03, 0.006], 0.12, 0.002),
    ),
    "ping": (
        lambda: partials([1760, 3520], [1, 0.08], [0.06, 0.02], 0.25, 0.005),
        lambda: partials([2349], [1], [0.018], 0.08, 0.004),
    ),
    "bubble": (
        lambda: sine(sweep(520, 1050, 0.12, 0.06), 0.12) * env(0.12, 0.004, 0.03),
        lambda: sine(sweep(800, 1300, 0.07, 0.03), 0.07) * env(0.07, 0.003, 0.015),
    ),
    "wood": (
        lambda: noise_ping(2200, 0.09, 0.02, 40) + 0.4 * noise_ping(5100, 0.09, 0.008, 30),
        lambda: noise_ping(3300, 0.05, 0.008, 40),
    ),
    "chime": (
        lambda: two_notes(),
        lambda: partials([1975.5, 3951], [1, 0.08], [0.022, 0.01], 0.08, 0.003),
    ),
}


def two_notes():
    """A quick rising fifth: E6 then B6."""
    a = partials([1318.5, 2637], [1, 0.1], [0.06, 0.02], 0.3, 0.003)
    b = partials([1975.5, 3951], [1, 0.1], [0.08, 0.025], 0.3, 0.003)
    out = np.zeros(int(RATE * 0.36))
    out[: len(a)] += a * 0.8
    start = int(RATE * 0.045)
    out[start:start + len(b)] += b[: len(out) - start]
    return out


def write(path, x, peak_db):
    x = room(np.asarray(x, dtype=float))
    x = x / (np.abs(x).max() + 1e-9) * 10 ** (peak_db / 20)
    pcm = (x * 32767).astype(np.int16)
    tmp = path + ".wav"
    with wave.open(tmp, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", tmp, "-c:a", "libvorbis", "-q:a", "6", path], check=True)
    os.remove(tmp)


def main(out):
    os.makedirs(out, exist_ok=True)
    for name, (click, hover) in STYLES.items():
        write(os.path.join(out, f"{name}_click.ogg"), click(), -10)
        write(os.path.join(out, f"{name}_hover.ogg"), hover(), -17)
        print(name)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "sounds_out")
