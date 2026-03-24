package com.djset.service;

import com.djset.model.Track;
import com.djset.scorer.TransitionScoreResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ScoringService {
    private static final double BPM_DIFF_STRICT = 1.0;
    private static final double BPM_DIFF_GOOD = 2.0;
    private static final double BPM_DIFF_ACCEPTABLE = 4.0;
    private static final double BPM_PERCENT_PENALTY_THRESHOLD = 0.03;
    private static final double CONFIDENCE_DEFAULT = 1.0;
    private static final double CONFIDENCE_MIN_FACTOR = 0.25;

    private static final double ENERGY_GRADUAL_RISE_MAX = 0.15;
    private static final double ENERGY_FLAT_DELTA = 0.05;
    private static final double ENERGY_SHARP_DROP = -0.20;

    private static final double GENRE_WEIGHT = 2.0;
    private static final double VIBE_WEIGHT = 1.0;

    public TransitionScoreResult scoreTransition(Track from, Track to) {
        validateTracks(from, to);
        List<String> reasons = new ArrayList<>();
        double bpmScore = scoreBpm(from, to, reasons);
        double keyScore = scoreKey(from, to, reasons);
        double energyScore = scoreEnergy(from, to, reasons);
        double styleScore = scoreStyleOverlap(from, to, reasons);
        double total = bpmScore + keyScore + energyScore + styleScore;
        return new TransitionScoreResult(total, bpmScore, keyScore, energyScore, styleScore, reasons);
    }

    private void validateTracks(Track from, Track to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Tracks cannot be null.");
        }
    }

    private double scoreBpm(Track from, Track to, List<String> reasons) {
        double diff = Math.abs(from.getBpm() - to.getBpm());
        double score;
        if (diff <= BPM_DIFF_STRICT) {
            score = 3.0;
            reasons.add("BPM diff <= 1: +3");
        } else if (diff <= BPM_DIFF_GOOD) {
            score = 2.0;
            reasons.add("BPM diff <= 2: +2");
        } else if (diff <= BPM_DIFF_ACCEPTABLE) {
            score = 1.0;
            reasons.add("BPM diff <= 4: +1");
        } else {
            score = -2.0;
            reasons.add("BPM diff > 4: -2");
        }

        if (hasBpmPercentPenalty(from, diff)) {
            score -= 1.0;
            reasons.add("BPM percent diff > 3%: -1");
        }
        double tempoConfidence = pairConfidence(from.getTempoConfidence(), to.getTempoConfidence());
        score = applyConfidenceWeight(score, tempoConfidence);
        reasons.add(String.format(Locale.US, "Tempo confidence factor %.2f applied", tempoConfidence));
        return score;
    }

    private boolean hasBpmPercentPenalty(Track from, double diff) {
        if (from.getBpm() <= 0) {
            return false;
        }
        double percentDiff = diff / from.getBpm();
        return percentDiff > BPM_PERCENT_PENALTY_THRESHOLD;
    }

    private double scoreKey(Track from, Track to, List<String> reasons) {
        CamelotKey k1 = CamelotKey.parse(from.getKey());
        CamelotKey k2 = CamelotKey.parse(to.getKey());
        double keyConfidence = pairConfidence(from.getKeyConfidence(), to.getKeyConfidence());
        if (k1 == null || k2 == null) {
            reasons.add("Unrecognized Camelot key: -2");
            return applyConfidenceWeight(-2.0, keyConfidence);
        }

        double score;
        if (k1.number == k2.number && k1.mode == k2.mode) {
            reasons.add("Same key: +3");
            score = 3.0;
        } else if (k1.isAdjacent(k2)) {
            reasons.add("Adjacent Camelot key: +2");
            score = 2.0;
        } else if (k1.number == k2.number && k1.mode != k2.mode) {
            reasons.add("Relative major/minor: +1");
            score = 1.0;
        } else {
            reasons.add("Key incompatible: -2");
            score = -2.0;
        }
        score = applyConfidenceWeight(score, keyConfidence);
        reasons.add(String.format(Locale.US, "Key confidence factor %.2f applied", keyConfidence));
        return score;
    }

    private double pairConfidence(double c1, double c2) {
        double n1 = normalizeConfidence(c1);
        double n2 = normalizeConfidence(c2);
        return Math.min(n1, n2);
    }

    private double normalizeConfidence(double value) {
        if (value <= 0.0) return CONFIDENCE_DEFAULT;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double applyConfidenceWeight(double score, double confidence) {
        double factor = CONFIDENCE_MIN_FACTOR + (confidence * (1.0 - CONFIDENCE_MIN_FACTOR));
        return score * factor;
    }

    private double scoreEnergy(Track from, Track to, List<String> reasons) {
        double delta = to.getEnergy() - from.getEnergy();
        if (delta > 0 && delta <= ENERGY_GRADUAL_RISE_MAX) {
            reasons.add("Gradual energy increase (<= 0.15): +2");
            return 2.0;
        }
        if (Math.abs(delta) <= ENERGY_FLAT_DELTA) {
            reasons.add("Flat/near-flat energy: +1");
            return 1.0;
        }
        if (delta < ENERGY_SHARP_DROP) {
            reasons.add("Sharp energy drop (< -0.20): -2");
            return -2.0;
        }
        reasons.add("Energy change neutral: +0");
        return 0.0;
    }

    private double scoreStyleOverlap(Track from, Track to, List<String> reasons) {
        double genreOverlap = jaccard(from.getGenreTags(), to.getGenreTags());
        double vibeOverlap = jaccard(from.getVibeTags(), to.getVibeTags());
        double score = (genreOverlap * GENRE_WEIGHT) + (vibeOverlap * VIBE_WEIGHT);
        reasons.add(String.format(Locale.US,
                "Style overlap genre=%.2f vibe=%.2f: +%.2f",
                genreOverlap, vibeOverlap, score));
        return score;
    }

    private double jaccard(List<String> a, List<String> b) {
        Set<String> setA = normalizeTags(a);
        Set<String> setB = normalizeTags(b);
        if (setA.isEmpty() && setB.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        if (union.isEmpty()) return 0.0;
        return (double) intersection.size() / union.size();
    }

    private Set<String> normalizeTags(List<String> tags) {
        Set<String> out = new HashSet<>();
        if (tags == null) return out;
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) {
                out.add(tag.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static final class CamelotKey {
        private final int number;
        private final char mode;

        private CamelotKey(int number, char mode) {
            this.number = number;
            this.mode = mode;
        }

        private static CamelotKey parse(String raw) {
            if (raw == null) return null;
            String s = raw.trim().toUpperCase(Locale.ROOT);
            if (s.length() < 2 || s.length() > 3) return null;

            char mode = s.charAt(s.length() - 1);
            if (mode != 'A' && mode != 'B') return null;

            String numPart = s.substring(0, s.length() - 1);
            int number;
            try {
                number = Integer.parseInt(numPart);
            } catch (NumberFormatException ex) {
                return null;
            }
            if (number < 1 || number > 12) return null;
            return new CamelotKey(number, mode);
        }

        private boolean isAdjacent(CamelotKey other) {
            if (this.mode != other.mode) return false;
            int diff = Math.abs(this.number - other.number);
            int wrapDiff = Math.min(diff, 12 - diff);
            return wrapDiff == 1;
        }
    }
}
