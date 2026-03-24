#!/usr/bin/env python3
import argparse
import json
import math
import sys


NOTE_NAMES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]

# Krumhansl major/minor templates (normalized by relative shape).
MAJOR_PROFILE = [6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88]
MINOR_PROFILE = [6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17]
MIN_BPM = 115.0
MAX_BPM = 175.0
KEY_CONFIDENCE_MIN = 0.15
KEY_CONFIDENCE_MIN_REKORDBOX_LIKE = 0.02


def _safe_imports():
    try:
        import librosa
        import numpy as np
        return librosa, np
    except Exception:
        return None, None


def estimate_key_camelot(chroma_mean, np):
    major = np.array(MAJOR_PROFILE, dtype=float)
    minor = np.array(MINOR_PROFILE, dtype=float)
    chroma = np.array(chroma_mean, dtype=float)

    best_score = -math.inf
    second_score = -math.inf
    best_idx = 0
    best_mode = "major"
    for i in range(12):
        major_score = np.dot(chroma, np.roll(major, i))
        minor_score = np.dot(chroma, np.roll(minor, i))
        for score, mode in [(major_score, "major"), (minor_score, "minor")]:
            if score > best_score:
                second_score = best_score
                best_score = score
                best_idx = i
                best_mode = mode
            elif score > second_score:
                second_score = score

    tonic = NOTE_NAMES[best_idx]
    key = to_camelot(tonic, best_mode)
    denom = abs(best_score) + 1e-9
    confidence = max(0.0, min(1.0, (best_score - second_score) / denom))
    return key, confidence


def estimate_key_segment_smoothed(chroma_matrix, np):
    if chroma_matrix.size == 0:
        return None, 0.0
    frames = chroma_matrix.shape[1]
    segment_size = 64
    votes = {}
    total_conf = 0.0
    for start in range(0, frames, segment_size):
        seg = chroma_matrix[:, start:start + segment_size]
        if seg.size == 0:
            continue
        key, conf = estimate_key_camelot(np.mean(seg, axis=1), np)
        if key is None:
            continue
        votes[key] = votes.get(key, 0.0) + conf
        total_conf += conf
    if not votes:
        return None, 0.0
    best_key = max(votes, key=votes.get)
    confidence = votes[best_key] / (total_conf + 1e-9)
    return best_key, float(confidence)


def normalize_bpm(bpm):
    if bpm <= 0:
        return 0.0
    value = float(bpm)
    # Bring tempos into practical DJ-planning band.
    while value < MIN_BPM:
        value *= 2.0
    while value > MAX_BPM:
        value /= 2.0
    return value


def estimate_tempo_confidence(librosa, np, onset_env, sr):
    if onset_env.size == 0:
        return 0.0
    pulse = librosa.beat.plp(onset_envelope=onset_env, sr=sr)
    if pulse.size == 0:
        return 0.0
    return max(0.0, min(1.0, float(np.mean(pulse)) * 2.0))


def clamp01(value):
    return max(0.0, min(1.0, float(value)))


def soft_norm(value, knee):
    v = max(0.0, float(value))
    k = max(1e-9, float(knee))
    return v / (v + k)


def to_camelot(note, mode):
    # Mapping standard note+mode to Camelot notation.
    mapping = {
        ("Ab", "minor"): "1A", ("G#", "minor"): "1A",
        ("B", "major"): "1B",
        ("Eb", "minor"): "2A", ("D#", "minor"): "2A",
        ("F#", "major"): "2B", ("Gb", "major"): "2B",
        ("Bb", "minor"): "3A", ("A#", "minor"): "3A",
        ("Db", "major"): "3B", ("C#", "major"): "3B",
        ("F", "minor"): "4A",
        ("Ab", "major"): "4B", ("G#", "major"): "4B",
        ("C", "minor"): "5A",
        ("Eb", "major"): "5B", ("D#", "major"): "5B",
        ("G", "minor"): "6A",
        ("Bb", "major"): "6B", ("A#", "major"): "6B",
        ("D", "minor"): "7A",
        ("F", "major"): "7B",
        ("A", "minor"): "8A",
        ("C", "major"): "8B",
        ("E", "minor"): "9A",
        ("G", "major"): "9B",
        ("B", "minor"): "10A",
        ("D", "major"): "10B",
        ("F#", "minor"): "11A", ("Gb", "minor"): "11A",
        ("A", "major"): "11B",
        ("C#", "minor"): "12A", ("Db", "minor"): "12A",
        ("E", "major"): "12B",
    }
    return mapping.get((note, mode), None)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, help="Input audio file")
    parser.add_argument("--mode", default="standard", choices=["standard", "rekordbox-like"])
    args = parser.parse_args()

    librosa, np = _safe_imports()
    if librosa is None:
        print(json.dumps({
            "bpm": 0.0,
            "tempoConfidence": 0.0,
            "key": None,
            "keyConfidence": 0.0,
            "energy": 0.0,
            "danceability": 0.0
        }))
        return 0

    try:
        y, sr = librosa.load(args.input, sr=22050, mono=True)
        if y is None or len(y) == 0:
            raise RuntimeError("empty-audio")

        tempo, _ = librosa.beat.beat_track(y=y, sr=sr)
        if isinstance(tempo, np.ndarray):
            tempo = float(tempo[0]) if tempo.size else 0.0
        bpm = float(tempo) if tempo is not None else 0.0
        bpm = normalize_bpm(bpm)

        chroma_cqt = librosa.feature.chroma_cqt(y=y, sr=sr)
        chroma_stft = librosa.feature.chroma_stft(y=y, sr=sr)
        chroma = (chroma_cqt + chroma_stft) / 2.0 if chroma_cqt.size and chroma_stft.size else chroma_cqt
        if args.mode == "rekordbox-like":
            key, key_confidence = estimate_key_segment_smoothed(chroma, np)
            key_threshold = KEY_CONFIDENCE_MIN_REKORDBOX_LIKE
        else:
            chroma_mean = np.mean(chroma, axis=1) if chroma.size else np.zeros((12,))
            key, key_confidence = estimate_key_camelot(chroma_mean, np)
            key_threshold = KEY_CONFIDENCE_MIN
        if key_confidence < key_threshold:
            key = None

        rms = librosa.feature.rms(y=y)[0]
        rms_mean = float(np.mean(rms)) if rms.size else 0.0
        spectral_centroid = librosa.feature.spectral_centroid(y=y, sr=sr)[0]
        centroid_mean = float(np.mean(spectral_centroid)) if spectral_centroid.size else 0.0
        centroid_norm = clamp01(centroid_mean / 4000.0)
        raw_energy = (rms_mean * 0.75) + (centroid_norm * 0.25)
        # Soft-knee normalization keeps the metric general while avoiding
        # saturation (most tracks landing at exactly 1.0).
        energy = (raw_energy * 1.8) / ((raw_energy * 1.8) + 0.35 + 1e-9)
        energy = clamp01(energy)

        onset_env = librosa.onset.onset_strength(y=y, sr=sr)
        tempo_confidence = estimate_tempo_confidence(librosa, np, onset_env, sr)
        onset_mean = float(np.mean(onset_env)) if onset_env.size else 0.0
        tempogram = librosa.feature.tempogram(onset_envelope=onset_env, sr=sr)
        tempo_stability = float(np.mean(np.max(tempogram, axis=0))) if tempogram.size else 0.0
        danceability_raw = (onset_mean * 0.6) + (tempo_stability * 0.4)
        danceability = clamp01(danceability_raw / 8.0)

        # High-level DJ attributes (0-10), deterministic weighted combinations.
        # Energy (driving feel): BPM + loudness.
        bpm_score = clamp01((bpm - 90.0) / 80.0)
        loudness_score = soft_norm(rms_mean, 0.12)
        dj_energy = 10.0 * ((bpm_score * 0.55) + (loudness_score * 0.45))

        # Intensity (aggressiveness): spectral brightness + spectral variation.
        centroid_score = clamp01((centroid_mean - 700.0) / 4300.0)
        stft_mag = np.abs(librosa.stft(y=y))
        if stft_mag.shape[1] > 1:
            spectral_flux_raw = float(np.mean(np.abs(np.diff(stft_mag, axis=1))))
        else:
            spectral_flux_raw = 0.0
        flux_score = soft_norm(spectral_flux_raw, 2.0)
        intensity = 10.0 * ((centroid_score * 0.6) + (flux_score * 0.4))

        # Tension (buildup/release potential): loudness variance + range + buildup.
        if rms.size > 0:
            loudness_variance = float(np.var(rms))
            loudness_range = float(np.max(rms) - np.min(rms))
            third = max(1, rms.size // 3)
            first_mean = float(np.mean(rms[:third]))
            last_mean = float(np.mean(rms[-third:]))
            buildup_raw = max(0.0, last_mean - first_mean)
        else:
            loudness_variance = 0.0
            loudness_range = 0.0
            buildup_raw = 0.0
        loudness_variance_score = soft_norm(loudness_variance, 0.002)
        dynamic_range_score = soft_norm(loudness_range, 0.10)
        buildup_score = soft_norm(buildup_raw, 0.05)
        tension = 10.0 * (
            (loudness_variance_score * 0.4)
            + (dynamic_range_score * 0.4)
            + (buildup_score * 0.2)
        )

        # Drop score (0-10): pronounced contrast + transient punch.
        if rms.size > 0:
            loud_q30 = float(np.percentile(rms, 30))
            loud_q90 = float(np.percentile(rms, 90))
            drop_contrast_raw = max(0.0, loud_q90 - loud_q30)
        else:
            drop_contrast_raw = 0.0
        onset_peak = float(np.max(onset_env)) if onset_env.size else 0.0
        onset_ratio = onset_peak / (onset_mean + 1e-9) if onset_mean > 0 else 0.0
        drop_contrast_score = soft_norm(drop_contrast_raw, 0.12)
        drop_punch_score = clamp01((onset_ratio - 1.0) / 2.0)
        drop_score = 10.0 * ((drop_contrast_score * 0.7) + (drop_punch_score * 0.3))

        print(json.dumps({
            "bpm": round(bpm, 3),
            "tempoConfidence": round(float(tempo_confidence), 4),
            "key": key,
            "keyConfidence": round(float(key_confidence), 4),
            "loudness": round(rms_mean, 5),
            "spectralCentroid": round(centroid_mean, 3),
            "spectralFlux": round(float(spectral_flux_raw), 5),
            "energy": round(energy, 3),
            "danceability": round(danceability, 3),
            "djEnergy": round(float(dj_energy), 3),
            "intensity": round(float(intensity), 3),
            "tension": round(float(tension), 3),
            "dropScore": round(float(drop_score), 3),
            "bpmScore": round(float(bpm_score), 4),
            "loudnessScore": round(float(loudness_score), 4),
            "centroidScore": round(float(centroid_score), 4),
            "fluxScore": round(float(flux_score), 4),
            "loudnessVarianceScore": round(float(loudness_variance_score), 4),
            "dynamicRangeScore": round(float(dynamic_range_score), 4),
            "buildupScore": round(float(buildup_score), 4)
        }))
        return 0
    except Exception:
        print(json.dumps({
            "bpm": 0.0,
            "tempoConfidence": 0.0,
            "key": None,
            "keyConfidence": 0.0,
            "loudness": 0.0,
            "spectralCentroid": 0.0,
            "spectralFlux": 0.0,
            "energy": 0.0,
            "danceability": 0.0,
            "djEnergy": 0.0,
            "intensity": 0.0,
            "tension": 0.0,
            "dropScore": 0.0,
            "bpmScore": 0.0,
            "loudnessScore": 0.0,
            "centroidScore": 0.0,
            "fluxScore": 0.0,
            "loudnessVarianceScore": 0.0,
            "dynamicRangeScore": 0.0,
            "buildupScore": 0.0
        }))
        return 0


if __name__ == "__main__":
    sys.exit(main())
