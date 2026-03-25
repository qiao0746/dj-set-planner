package com.djset.service;

import com.djset.model.NextSongIntent;
import com.djset.model.NextSongRecommendationItem;
import com.djset.model.Track;
import com.djset.scorer.TransitionScoreResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Ranks candidate tracks after the current track using {@link ScoringService} transition scores
 * plus small intent-specific adjustments (energy, tension, intensity, sub-score emphasis).
 * <p>
 * Intent tweaks follow common DJ practice: mixability (BPM/key/style) stays in the base score;
 * uplift biases a perceptible lift without always maximizing every psychoacoustic axis at once;
 * chill favors release (lower tension/intensity/energy) and reinforces smooth tempo/key;
 * groovy keeps energy and “feel” (danceability) steady while reinforcing style and mixability.
 */
public final class NextSongRecommendationService {

    public static final int MIN_RECOMMENDATIONS = 1;
    public static final int MAX_RECOMMENDATIONS = 20;
    public static final int DEFAULT_RECOMMENDATIONS = 5;

    private final ScoringService scoringService = new ScoringService();

    public List<NextSongRecommendationItem> recommend(
            Track current,
            List<Track> candidates,
            NextSongIntent intent,
            int numberOfRecommendations
    ) {
        Objects.requireNonNull(current, "current");
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("Candidate tracks cannot be empty.");
        }
        if (numberOfRecommendations < MIN_RECOMMENDATIONS || numberOfRecommendations > MAX_RECOMMENDATIONS) {
            throw new IllegalArgumentException(
                    "numberOfRecommendations must be between " + MIN_RECOMMENDATIONS + " and " + MAX_RECOMMENDATIONS + ".");
        }

        String currentId = current.getId();
        List<NextSongRecommendationItem> rows = new ArrayList<>();
        for (Track candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (currentId != null && currentId.equals(candidate.getId())) {
                continue;
            }
            TransitionScoreResult tr = scoringService.scoreTransition(current, candidate);
            double adjustment = intentAdjustment(current, candidate, tr, intent);
            double finalScore = tr.getTotalScore() + adjustment;
            String explanation = buildExplanation(intent, current, candidate, tr, finalScore, adjustment);
            String mix = MixSuggestion.fromTransitionScore(tr);
            rows.add(new NextSongRecommendationItem(candidate, finalScore, explanation, mix));
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("No candidates after excluding the current track.");
        }

        rows.sort(Comparator
                .comparingDouble(NextSongRecommendationItem::getFinalScore)
                .reversed()
                .thenComparing(r -> r.getTrack().getId(), Comparator.nullsLast(String::compareTo)));

        int limit = Math.min(numberOfRecommendations, rows.size());
        return rows.subList(0, limit);
    }

    private static double intentAdjustment(Track from, Track to, TransitionScoreResult tr, NextSongIntent intent) {
        return switch (intent) {
            case UPLIFT -> upliftAdjustment(from, to);
            case CHILL -> chillAdjustment(from, to, tr);
            case GROOVY -> groovyAdjustment(from, to, tr);
        };
    }

    /**
     * Prefer a clear energy lift; after a knee, extra energy gains count for less so we do not
     * always pick the most extreme track (sets usually climb gradually rather than in one cliff).
     * Slightly reward rising tension/intensity without matching the full weight of energy.
     */
    private static double upliftAdjustment(Track from, Track to) {
        double dEnergy = to.getEnergy() - from.getEnergy();
        double dTension = to.getTension() - from.getTension();
        double dIntensity = to.getIntensity() - from.getIntensity();
        double adj = upliftEnergyBonus(dEnergy);
        adj += clamp(dTension * 1.25, -0.6, 1.2);
        adj += clamp(dIntensity * 1.25, -0.6, 1.2);
        if (dEnergy > 0.22 && dTension > 0.18 && dIntensity > 0.18) {
            adj -= 0.28;
        }
        return adj;
    }

    /** Strong reward for modest lifts; gentler slope past {@code ~0.16} to avoid “max everything” picks. */
    private static double upliftEnergyBonus(double dEnergy) {
        if (dEnergy <= 0) {
            return clamp(dEnergy * 4.0, -1.2, 0.0);
        }
        double knee = 0.16;
        double steep = 4.0;
        double gentle = 1.15;
        double linear = dEnergy <= knee ? steep * dEnergy : steep * knee + gentle * (dEnergy - knee);
        return clamp(linear, 0.0, 2.8);
    }

    /**
     * Prefer lower tension/intensity and a slight energy settle; reinforce smooth BPM/key from
     * the same transition model.
     */
    private static double chillAdjustment(Track from, Track to, TransitionScoreResult tr) {
        double dropT = from.getTension() - to.getTension();
        double dropI = from.getIntensity() - to.getIntensity();
        double dropE = from.getEnergy() - to.getEnergy();
        double adj = clamp(dropT * 2.2, 0.0, 2.2) + clamp(dropI * 2.2, 0.0, 2.2);
        adj += clamp(dropE * 1.05, 0.0, 1.35);
        adj += 0.14 * tr.getBpmScore() + 0.14 * tr.getKeyScore();
        return adj;
    }

    /**
     * Prefer similar energy and danceability (“groove lock”); emphasize style + BPM/key
     * compatibility already in base score.
     */
    private static double groovyAdjustment(Track from, Track to, TransitionScoreResult tr) {
        double dE = Math.abs(to.getEnergy() - from.getEnergy());
        double energyFit = clamp(1.9 - dE * 7.0, -1.4, 1.9);
        double dDance = Math.abs(to.getDanceability() - from.getDanceability());
        double danceFit = clamp(1.05 - dDance * 4.5, -0.55, 1.05);
        double adj = energyFit + danceFit + 0.14 * tr.getStyleScore() + 0.11 * (tr.getBpmScore() + tr.getKeyScore());
        return adj;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String buildExplanation(
            NextSongIntent intent,
            Track from,
            Track to,
            TransitionScoreResult tr,
            double finalScore,
            double adjustment
    ) {
        double base = tr.getTotalScore();
        String mix = base >= 4.0 ? "Strong transition fit" : base >= 1.5 ? "Good mixability" : "Tighter mixing advised";
        double dEnergy = to.getEnergy() - from.getEnergy();
        return switch (intent) {
            case UPLIFT -> String.format(Locale.US,
                    "%s (final %.2f, base %.2f, intent %+.2f) — lifts energy (Δ %.2f) with a gradual bias vs extreme jumps.",
                    mix, finalScore, base, adjustment, dEnergy);
            case CHILL -> String.format(Locale.US,
                    "%s (final %.2f, base %.2f, intent %+.2f) — eases tension/intensity/energy and smooth tempo/key for the chill.",
                    mix, finalScore, base, adjustment);
            case GROOVY -> String.format(Locale.US,
                    "%s (final %.2f, base %.2f, intent %+.2f) — locks energy & danceability (|Δenergy| %.2f) for groovy flow.",
                    mix, finalScore, base, adjustment, Math.abs(dEnergy));
        };
    }
}
