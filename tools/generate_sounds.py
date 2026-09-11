"""Generate and verify Flux Pylons runtime audio.

All source waveforms are synthesized deterministically. Runtime files are encoded
directly from float PCM to Ogg Vorbis, so no temporary WAV files are created.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import tempfile

import numpy as np


SAMPLE_RATE = 32_000
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/quantumflux/sounds"
MANIFEST = ROOT / "src/main/resources/assets/quantumflux/sounds.json"
NAMES = ("gadget_on", "gadget_off", "pylon_hum")
QUALITY = {"gadget_on": 4, "gadget_off": 4, "pylon_hum": 3}


def db_to_amplitude(db: float) -> float:
    return 10.0 ** (db / 20.0)


def amplitude_to_db(value: float) -> float:
    return 20.0 * math.log10(max(float(value), 1.0e-12))


def smoothstep(value: np.ndarray) -> np.ndarray:
    value = np.clip(value, 0.0, 1.0)
    return value * value * (3.0 - 2.0 * value)


def fade_edges(audio: np.ndarray, attack_seconds: float, release_seconds: float) -> np.ndarray:
    result = np.array(audio, dtype=np.float64, copy=True)
    attack = min(len(result), max(1, round(attack_seconds * SAMPLE_RATE)))
    release = min(len(result), max(1, round(release_seconds * SAMPLE_RATE)))
    result[:attack] *= np.sin(np.linspace(0.0, math.pi / 2.0, attack, endpoint=True)) ** 2
    result[-release:] *= np.cos(np.linspace(0.0, math.pi / 2.0, release, endpoint=True)) ** 2
    return result


def finalize(audio: np.ndarray, target_rms_db: float, peak_ceiling_db: float,
             attack_seconds: float, release_seconds: float) -> np.ndarray:
    result = np.asarray(audio, dtype=np.float64)
    result = result - np.mean(result)
    result = fade_edges(result, attack_seconds, release_seconds)
    rms = math.sqrt(float(np.mean(result * result)))
    if rms <= 1.0e-12:
        raise ValueError("Cannot normalize silent audio")
    result *= db_to_amplitude(target_rms_db) / rms
    peak = float(np.max(np.abs(result)))
    ceiling = db_to_amplitude(peak_ceiling_db)
    if peak > ceiling:
        result *= ceiling / peak
    return result.astype(np.float32)


def band_limited_noise(length: int, seed: int, low_hz: float, high_hz: float,
                       spectral_slope: float = 0.0) -> np.ndarray:
    """Return deterministic noise whose energy is smoothly confined to a band."""
    frequencies = np.fft.rfftfreq(length, 1.0 / SAMPLE_RATE)
    rng = np.random.RandomState(seed)
    phases = rng.uniform(0.0, 2.0 * math.pi, len(frequencies))

    low_width = max(40.0, low_hz * 0.6)
    high_width = max(120.0, high_hz * 0.25)
    low_gate = smoothstep((frequencies - (low_hz - low_width)) / low_width)
    high_gate = smoothstep(((high_hz + high_width) - frequencies) / high_width)
    reference = np.maximum(frequencies, max(low_hz, 20.0)) / max(low_hz, 20.0)
    shape = low_gate * high_gate * np.power(reference, spectral_slope)
    shape[0] = 0.0

    spectrum = shape * np.exp(1j * phases)
    noise = np.fft.irfft(spectrum, n=length)
    rms = math.sqrt(float(np.mean(noise * noise)))
    return noise / max(rms, 1.0e-12)


def glide(duration: float, start_hz: float, end_hz: float, phase_offset: float = 0.0) -> np.ndarray:
    length = round(duration * SAMPLE_RATE)
    progress = np.arange(length, dtype=np.float64) / max(length - 1, 1)
    shaped = smoothstep(progress)
    frequency = start_hz * np.power(end_hz / start_hz, shaped)
    phase = phase_offset + 2.0 * math.pi * np.cumsum(frequency) / SAMPLE_RATE
    return np.sin(phase)


def add_layer(destination: np.ndarray, layer: np.ndarray, offset_seconds: float,
              gain: float = 1.0) -> None:
    start = round(offset_seconds * SAMPLE_RATE)
    end = min(len(destination), start + len(layer))
    if end > start:
        destination[start:end] += layer[:end - start] * gain


def mechanical_contact(seed: int, bright: bool) -> np.ndarray:
    duration = 0.052
    length = round(duration * SAMPLE_RATE)
    time = np.arange(length, dtype=np.float64) / SAMPLE_RATE
    high_hz = 3_800.0 if bright else 3_100.0
    noise = band_limited_noise(length, seed, 260.0, high_hz, spectral_slope=-0.18)
    envelope = (1.0 - np.exp(-time * 2_800.0)) * np.exp(-time * 112.0)
    body = np.sin(2.0 * math.pi * 390.0 * time + 0.25) * np.exp(-time * 74.0)
    overtone = np.sin(2.0 * math.pi * 780.0 * time + 1.1) * np.exp(-time * 102.0)
    contact = noise * envelope * 0.34 + body * 0.20 + overtone * 0.07
    return fade_edges(contact, 0.0005, 0.010)


def generate_gadget_on() -> np.ndarray:
    """A tactile relay contact followed by a restrained, confident energy lock."""
    duration = 0.410
    audio = np.zeros(round(duration * SAMPLE_RATE), dtype=np.float64)
    add_layer(audio, mechanical_contact(0x51464F4E, bright=True), 0.0, 0.92)

    charge_duration = 0.285
    charge = glide(charge_duration, 225.0, 685.0, phase_offset=0.15)
    charge += glide(charge_duration, 337.5, 1_027.5, phase_offset=1.0) * 0.17
    charge_time = np.arange(len(charge), dtype=np.float64) / SAMPLE_RATE
    charge_envelope = smoothstep(charge_time / 0.032) * smoothstep(
        (charge_duration - charge_time) / 0.072
    )
    charge *= charge_envelope * 0.33
    add_layer(audio, charge, 0.020)

    lock_duration = 0.150
    lock_time = np.arange(round(lock_duration * SAMPLE_RATE), dtype=np.float64) / SAMPLE_RATE
    lock_envelope = (1.0 - np.exp(-lock_time * 520.0)) * np.exp(-lock_time * 31.0)
    lock = (
        np.sin(2.0 * math.pi * 770.0 * lock_time + 0.4)
        + np.sin(2.0 * math.pi * 1_155.0 * lock_time + 1.2) * 0.28
    ) * lock_envelope
    add_layer(audio, lock, 0.235, 0.115)

    return finalize(audio, target_rms_db=-19.0, peak_ceiling_db=-6.0,
                    attack_seconds=0.001, release_seconds=0.026)


def generate_gadget_off() -> np.ndarray:
    """A controlled field collapse with a soft mechanical release."""
    duration = 0.330
    audio = np.zeros(round(duration * SAMPLE_RATE), dtype=np.float64)

    discharge_duration = 0.238
    discharge = glide(discharge_duration, 610.0, 185.0, phase_offset=0.9)
    discharge += glide(discharge_duration, 915.0, 277.5, phase_offset=0.1) * 0.14
    discharge_time = np.arange(len(discharge), dtype=np.float64) / SAMPLE_RATE
    discharge_envelope = smoothstep(discharge_time / 0.010) * smoothstep(
        (discharge_duration - discharge_time) / 0.055
    )
    discharge *= discharge_envelope * 0.31
    add_layer(audio, discharge, 0.0)

    release = mechanical_contact(0x51464F46, bright=False)
    add_layer(audio, release, 0.180, 0.72)

    settle_time = np.arange(round(0.115 * SAMPLE_RATE), dtype=np.float64) / SAMPLE_RATE
    settle = np.sin(2.0 * math.pi * 145.0 * settle_time + 0.5) * np.exp(-settle_time * 39.0)
    add_layer(audio, settle, 0.190, 0.075)

    return finalize(audio, target_rms_db=-19.5, peak_ceiling_db=-6.5,
                    attack_seconds=0.002, release_seconds=0.030)


def quietest_loop_cut(audio: np.ndarray, radius: int = 48) -> np.ndarray:
    """Rotate a periodic waveform so its buffer boundary falls at a gentle slope."""
    delta = np.abs(audio - np.roll(audio, 1))
    kernel = np.ones(radius, dtype=np.float64) / radius
    score = np.convolve(np.concatenate((delta[-radius:], delta, delta[:radius])), kernel, mode="same")
    score = score[radius:radius + len(audio)]
    cut = int(np.argmin(score))
    return np.roll(audio, -cut)


def generate_pylon_hum() -> np.ndarray:
    """A four-second periodic field hum built only from exact loop harmonics."""
    duration = 4.0
    length = round(duration * SAMPLE_RATE)
    time = np.arange(length, dtype=np.float64) / SAMPLE_RATE

    slow_motion = 1.0 + 0.065 * np.sin(2.0 * math.pi * 0.25 * time + 0.8)
    slow_motion += 0.025 * np.sin(2.0 * math.pi * 0.50 * time + 2.0)
    field = (
        np.sin(2.0 * math.pi * 55.0 * time + 0.2)
        + np.sin(2.0 * math.pi * 82.5 * time + 1.0) * 0.19
        + np.sin(2.0 * math.pi * 110.0 * time + 0.7) * 0.28
        + np.sin(2.0 * math.pi * 165.0 * time + 1.9) * 0.10
        + np.sin(2.0 * math.pi * 220.0 * time + 0.4) * 0.045
    ) * slow_motion

    shimmer_motion = 0.72 + 0.28 * np.sin(2.0 * math.pi * 0.75 * time + 1.1)
    shimmer = (
        np.sin(2.0 * math.pi * 330.0 * time + 1.6)
        + np.sin(2.0 * math.pi * 440.0 * time + 0.3) * 0.36
    ) * shimmer_motion * 0.024

    texture = band_limited_noise(length, 0x51464855, 34.0, 720.0, spectral_slope=-0.82)
    texture *= (0.82 + 0.18 * np.sin(2.0 * math.pi * 0.50 * time + 0.25)) * 0.026

    hum = field + shimmer + texture
    hum -= np.mean(hum)
    hum = quietest_loop_cut(hum)
    rms = math.sqrt(float(np.mean(hum * hum)))
    hum *= db_to_amplitude(-19.0) / rms
    peak = float(np.max(np.abs(hum)))
    ceiling = db_to_amplitude(-9.0)
    if peak > ceiling:
        hum *= ceiling / peak
    return hum.astype(np.float32)


def generate_all() -> dict[str, np.ndarray]:
    return {
        "gadget_on": generate_gadget_on(),
        "gadget_off": generate_gadget_off(),
        "pylon_hum": generate_pylon_hum(),
    }


def validate_manifest() -> None:
    try:
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise RuntimeError(f"Unable to read {MANIFEST}: {error}") from error
    if set(manifest) != set(NAMES):
        raise RuntimeError(f"{MANIFEST.name}: expected exactly these events: {', '.join(NAMES)}")
    for name in NAMES:
        event = manifest[name]
        expected_subtitle = f"subtitles.quantumflux.{name}"
        if event.get("subtitle") != expected_subtitle:
            raise RuntimeError(f"{MANIFEST.name}: {name} needs subtitle {expected_subtitle}")
        entries = event.get("sounds")
        if not isinstance(entries, list) or len(entries) != 1:
            raise RuntimeError(f"{MANIFEST.name}: {name} must resolve to one runtime file")
        entry = entries[0]
        if entry.get("name") != f"quantumflux:{name}":
            raise RuntimeError(f"{MANIFEST.name}: {name} has the wrong runtime resource")
        if entry.get("stream") is not False or entry.get("preload") is not True:
            raise RuntimeError(f"{MANIFEST.name}: {name} must be buffered and preloaded")


def require_tool(name: str) -> str:
    path = shutil.which(name)
    if path is None:
        raise RuntimeError(f"{name} is required to generate and validate audio")
    return path


def run(command: list[str], input_bytes: bytes | None = None) -> bytes:
    result = subprocess.run(command, input=input_bytes, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, check=False)
    if result.returncode != 0:
        message = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"Command failed ({result.returncode}): {message}")
    return result.stdout


def encode_ogg(audio: np.ndarray, destination: Path, quality: int,
               end_padding_samples: int = 0) -> None:
    ffmpeg = require_tool("ffmpeg")
    command = [
        ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error",
        "-f", "f32le", "-ar", str(SAMPLE_RATE), "-ac", "1", "-i", "pipe:0",
        "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact",
        "-c:a", "libvorbis", "-q:a", str(quality), "-ar", str(SAMPLE_RATE),
        "-ac", "1", "-f", "ogg", "-y", str(destination),
    ]
    encoded_audio = np.asarray(audio, dtype="<f4")
    if end_padding_samples:
        # libvorbis trims 128 silent tail samples from these short one-shots.
        # Supplying that tail preserves the designed audible duration.
        encoded_audio = np.pad(encoded_audio, (0, end_padding_samples))
    pcm = encoded_audio.tobytes(order="C")
    run(command, input_bytes=pcm)


def decode_ogg(path: Path) -> np.ndarray:
    ffmpeg = require_tool("ffmpeg")
    command = [
        ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error", "-i", str(path),
        "-map", "0:a:0", "-f", "f32le", "-ar", str(SAMPLE_RATE), "-ac", "1", "pipe:1",
    ]
    return np.frombuffer(run(command), dtype="<f4").astype(np.float64)


def probe_ogg(path: Path) -> dict[str, object]:
    ffprobe = require_tool("ffprobe")
    command = [
        ffprobe, "-v", "error", "-select_streams", "a:0",
        "-show_entries", "stream=codec_name,sample_rate,channels,channel_layout,duration",
        "-of", "json", str(path),
    ]
    payload = json.loads(run(command).decode("utf-8"))
    streams = payload.get("streams", [])
    if len(streams) != 1:
        raise RuntimeError(f"{path.name}: expected exactly one audio stream")
    return streams[0]


def spectral_ratio(audio: np.ndarray, lower_hz: float) -> float:
    window = np.hanning(len(audio))
    spectrum = np.abs(np.fft.rfft(audio * window)) ** 2
    frequencies = np.fft.rfftfreq(len(audio), 1.0 / SAMPLE_RATE)
    total = float(np.sum(spectrum))
    return float(np.sum(spectrum[frequencies >= lower_hz])) / max(total, 1.0e-20)


def validate_file(name: str, path: Path, expected_samples: int) -> dict[str, float]:
    stream = probe_ogg(path)
    if stream.get("codec_name") != "vorbis":
        raise RuntimeError(f"{path.name}: expected Vorbis, got {stream.get('codec_name')}")
    if int(stream.get("sample_rate", 0)) != SAMPLE_RATE:
        raise RuntimeError(f"{path.name}: expected {SAMPLE_RATE} Hz")
    if int(stream.get("channels", 0)) != 1:
        raise RuntimeError(f"{path.name}: expected mono audio")

    decoded = decode_ogg(path)
    if abs(len(decoded) - expected_samples) > 1:
        raise RuntimeError(
            f"{path.name}: decoded {len(decoded)} samples, expected {expected_samples}"
        )
    peak = float(np.max(np.abs(decoded)))
    rms = math.sqrt(float(np.mean(decoded * decoded)))
    dc = abs(float(np.mean(decoded)))
    peak_db = amplitude_to_db(peak)
    rms_db = amplitude_to_db(rms)
    high_ratio = spectral_ratio(decoded, 8_000.0)

    if peak_db > -3.0:
        raise RuntimeError(f"{path.name}: unsafe peak {peak_db:.2f} dBFS")
    if not -27.0 <= rms_db <= -13.0:
        raise RuntimeError(f"{path.name}: unexpected RMS {rms_db:.2f} dBFS")
    if dc > 0.002:
        raise RuntimeError(f"{path.name}: DC offset {dc:.6f} is too high")
    if high_ratio > 0.02:
        raise RuntimeError(f"{path.name}: excess energy above 8 kHz ({high_ratio:.4%})")

    metrics = {
        "peak_db": peak_db,
        "rms_db": rms_db,
        "dc": dc,
        "high_ratio": high_ratio,
    }
    if name == "pylon_hum":
        jump = abs(float(decoded[0] - decoded[-1]))
        adjacent = np.abs(np.diff(decoded))
        p99_step = float(np.percentile(adjacent, 99.0))
        seam_ratio = jump / max(p99_step, 1.0e-12)
        slope_mismatch = abs(float((decoded[1] - decoded[0]) - (decoded[0] - decoded[-1])))
        if jump > 0.006 or seam_ratio > 1.5 or slope_mismatch > 0.008:
            raise RuntimeError(
                f"{path.name}: loop seam failed (jump={jump:.6f}, "
                f"ratio={seam_ratio:.3f}, slope={slope_mismatch:.6f})"
            )
        metrics.update({
            "seam_jump": jump,
            "seam_ratio": seam_ratio,
            "slope_mismatch": slope_mismatch,
        })
    return metrics


def render(directory: Path, sounds: dict[str, np.ndarray]) -> dict[str, dict[str, float]]:
    directory.mkdir(parents=True, exist_ok=True)
    for name, audio in sounds.items():
        padding = 0 if name == "pylon_hum" else 128
        encode_ogg(audio, directory / f"{name}.ogg", QUALITY[name], padding)
    return {
        name: validate_file(name, directory / f"{name}.ogg", len(audio))
        for name, audio in sounds.items()
    }


def format_metrics(name: str, audio: np.ndarray, path: Path,
                   metrics: dict[str, float]) -> str:
    digest = hashlib.sha256(path.read_bytes()).hexdigest()[:12]
    details = (
        f"{name}.ogg: {len(audio) / SAMPLE_RATE:.3f}s, {path.stat().st_size} bytes, "
        f"peak {metrics['peak_db']:.2f} dBFS, RMS {metrics['rms_db']:.2f} dBFS, "
        f">8kHz {metrics['high_ratio']:.4%}, sha256 {digest}"
    )
    if name == "pylon_hum":
        details += (
            f", seam {metrics['seam_jump']:.6f}, "
            f"seam/p99-step {metrics['seam_ratio']:.3f}"
        )
    return details


def check_assets(sounds: dict[str, np.ndarray]) -> int:
    missing = [name for name in NAMES if not (OUT / f"{name}.ogg").is_file()]
    if missing:
        print("Missing generated assets: " + ", ".join(f"{name}.ogg" for name in missing))
        return 1

    with tempfile.TemporaryDirectory(prefix="quantumflux-audio-check-") as temp:
        temp_dir = Path(temp)
        metrics = render(temp_dir, sounds)
        mismatches = []
        for name in NAMES:
            generated = temp_dir / f"{name}.ogg"
            committed = OUT / f"{name}.ogg"
            if generated.read_bytes() != committed.read_bytes():
                mismatches.append(f"{name}.ogg")
            print(format_metrics(name, sounds[name], generated, metrics[name]))

    if mismatches:
        print("Generated assets differ: " + ", ".join(mismatches))
        print("Run: python tools/generate_sounds.py")
        return 1
    print("Audio assets are byte-identical to deterministic generator output.")
    return 0


def write_assets(sounds: dict[str, np.ndarray]) -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="quantumflux-audio-build-") as temp:
        temp_dir = Path(temp)
        metrics = render(temp_dir, sounds)
        for name in NAMES:
            generated = temp_dir / f"{name}.ogg"
            destination = OUT / f"{name}.ogg"
            destination.write_bytes(generated.read_bytes())
            print(format_metrics(name, sounds[name], destination, metrics[name]))
    print("Generated three original mono Ogg Vorbis assets with no intermediate WAV files.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check", action="store_true",
        help="regenerate in a temporary directory and require byte-identical shipped assets",
    )
    args = parser.parse_args()
    validate_manifest()
    sounds = generate_all()
    return check_assets(sounds) if args.check else write_assets(sounds)


if __name__ == "__main__":
    raise SystemExit(main())
