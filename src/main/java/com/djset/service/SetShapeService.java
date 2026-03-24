package com.djset.service;

import com.djset.model.SetShapePlan;
import com.djset.model.SetShapeSlotDebug;
import com.djset.model.Track;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SetShapeService {
    private static final String MODE_CLUB = "club";
    private static final String MODE_RAVE = "rave";
    private static final String MODE_HOUSE_PARTY = "house_party";
    private static final int MAX_SWAP_PASSES = 3;
    private static final int MAX_SMOOTHING_PASSES = 2;

    private final ScoringService scoringService;

    public SetShapeService() {
        this(new ScoringService());
    }

    public SetShapeService(ScoringService scoringService) {
        this.scoringService = scoringService;
    }

    /**
     * Shape tracks to the mode curve (full reordering).
     */
    public SetShapePlan shapeSet(List<Track> tracks, String mode) {
        return shapeSet(tracks, mode, false);
    }

    /**
     * Shape tracks to the mode curve. When {@code pinFirstTrack} is true, {@code tracks.get(0)} stays at
     * slot 0 for the whole optimization (opening track fixed).
     */
    public SetShapePlan shapeSet(List<Track> tracks, String mode, boolean pinFirstTrack) {
        if (tracks == null || tracks.isEmpty()) {
            throw new IllegalArgumentException("Track list cannot be empty.");
        }
        String normalizedMode = normalizeMode(mode);
        int n = tracks.size();
        CurveTemplate curve = buildCurveTemplate(normalizedMode, n);
        List<Track> ordered = assignTracksToCurve(tracks, curve, normalizedMode, pinFirstTrack);
        optimizeNeighbors(ordered, curve, normalizedMode, pinFirstTrack);
        smoothTransitions(ordered, curve, normalizedMode, pinFirstTrack);
        List<SetShapeSlotDebug> debug = buildDebug(ordered, curve, normalizedMode);
        return new SetShapePlan(
                normalizedMode,
                toList(curve.targetEnergy),
                toList(curve.targetTension),
                ordered,
                debug
        );
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return MODE_CLUB;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        if (!MODE_CLUB.equals(normalized) && !MODE_RAVE.equals(normalized) && !MODE_HOUSE_PARTY.equals(normalized)) {
            throw new IllegalArgumentException("Mode must be one of: club, rave, house_party.");
        }
        return normalized;
    }

    private CurveTemplate buildCurveTemplate(String mode, int n) {
        double[] energy = new double[n];
        double[] tension = new double[n];
        double[] intensity = new double[n];

        for (int i = 0; i < n; i++) {
            double p = n == 1 ? 1.0 : ((double) i / (n - 1));
            if (MODE_CLUB.equals(mode)) {
                energy[i] = clamp10(4.5 + (4.0 * p) + (0.4 * Math.sin(i * 1.3)));
                tension[i] = clamp10(3.8 + (3.2 * p) + (0.5 * Math.sin(i * 1.1 + 0.4)));
                intensity[i] = clamp10(4.2 + (2.2 * p));
            } else if (MODE_RAVE.equals(mode)) {
                energy[i] = clamp10(5.0 + (2.8 * Math.sin(i * 1.5)) + (1.8 * p));
                tension[i] = clamp10(4.5 + (3.0 * Math.sin(i * 1.7 + 0.8)) + (2.0 * p));
                intensity[i] = clamp10(5.5 + (2.5 * Math.sin(i * 1.25 + 0.5)) + (1.0 * p));
            } else {
                // house_party
                energy[i] = clamp10(5.0 + (1.7 * p) + (0.6 * Math.sin(i * 1.2)));
                tension[i] = clamp10(3.2 + (1.6 * p) + (0.45 * Math.sin(i * 1.1 + 0.3)));
                intensity[i] = clamp10(4.3 + (0.9 * p) + (0.4 * Math.sin(i * 0.9)));
            }
        }
        return new CurveTemplate(energy, tension, intensity);
    }

    private List<Track> assignTracksToCurve(List<Track> tracks, CurveTemplate curve, String mode, boolean pinFirst) {
        if (pinFirst) {
            Track opener = tracks.get(0);
            List<Track> mutable = new ArrayList<>();
            boolean skippedOpener = false;
            for (Track t : tracks) {
                if (!skippedOpener && sameOpeningTrack(t, opener)) {
                    skippedOpener = true;
                    continue;
                }
                mutable.add(t);
            }
            List<Track> ordered = new ArrayList<>();
            ordered.add(opener);
            for (int slot = 1; slot < curve.targetEnergy.length; slot++) {
                Track best = null;
                double bestScore = Double.NEGATIVE_INFINITY;
                for (Track candidate : mutable) {
                    double score = slotFitScore(candidate, slot, curve, mode);
                    if (best == null || score > bestScore || (score == bestScore && compareTrackId(candidate, best) < 0)) {
                        best = candidate;
                        bestScore = score;
                    }
                }
                ordered.add(best);
                mutable.remove(best);
            }
            return ordered;
        }
        List<Track> remaining = tracks.stream()
                .sorted(Comparator.comparing(t -> t.getId() == null ? "" : t.getId()))
                .toList();
        List<Track> mutable = new ArrayList<>(remaining);
        List<Track> ordered = new ArrayList<>();
        for (int slot = 0; slot < curve.targetEnergy.length; slot++) {
            Track best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (Track candidate : mutable) {
                double score = slotFitScore(candidate, slot, curve, mode);
                if (best == null || score > bestScore || (score == bestScore && compareTrackId(candidate, best) < 0)) {
                    best = candidate;
                    bestScore = score;
                }
            }
            ordered.add(best);
            mutable.remove(best);
        }
        return ordered;
    }

    private boolean sameOpeningTrack(Track a, Track opener) {
        if (a == opener) {
            return true;
        }
        String idA = a.getId();
        String idO = opener.getId();
        if (idA != null && idO != null) {
            return idA.equals(idO);
        }
        return false;
    }

    private void optimizeNeighbors(List<Track> ordered, CurveTemplate curve, String mode, boolean pinFirst) {
        int start = pinFirst ? 1 : 0;
        for (int pass = 0; pass < MAX_SWAP_PASSES; pass++) {
            boolean improvedAny = false;
            for (int i = start; i < ordered.size() - 1; i++) {
                double before = localObjective(ordered, i, curve, mode);
                swap(ordered, i, i + 1);
                double after = localObjective(ordered, i, curve, mode);
                if (after > before) {
                    improvedAny = true;
                } else {
                    swap(ordered, i, i + 1);
                }
            }
            if (!improvedAny) {
                return;
            }
        }
    }

    private double localObjective(List<Track> ordered, int i, CurveTemplate curve, String mode) {
        int start = Math.max(0, i - 1);
        int end = Math.min(ordered.size() - 1, i + 2);
        double score = 0.0;
        for (int idx = start; idx <= end; idx++) {
            score += slotFitScore(ordered.get(idx), idx, curve, mode);
            if (idx < ordered.size() - 1) {
                score += transitionWeight(mode) * pairSmoothnessScore(ordered.get(idx), ordered.get(idx + 1), mode);
            }
        }
        return score;
    }

    private void smoothTransitions(List<Track> ordered, CurveTemplate curve, String mode, boolean pinFirst) {
        if (ordered.size() < 3) {
            return;
        }
        for (int pass = 0; pass < MAX_SMOOTHING_PASSES; pass++) {
            boolean improved = false;
            for (int from = 0; from < ordered.size(); from++) {
                if (pinFirst && from == 0) {
                    continue;
                }
                double baseline = globalObjective(ordered, curve, mode);
                Track moving = ordered.remove(from);
                int bestIndex = from;
                double bestScore = baseline;

                for (int to = 0; to <= ordered.size(); to++) {
                    if (pinFirst && to == 0) {
                        continue;
                    }
                    ordered.add(to, moving);
                    double score = globalObjective(ordered, curve, mode);
                    if (score > bestScore + 1e-9) {
                        bestScore = score;
                        bestIndex = to;
                    }
                    ordered.remove(to);
                }
                ordered.add(bestIndex, moving);
                if (bestIndex != from) {
                    improved = true;
                }
            }
            if (!improved) {
                return;
            }
        }
    }

    private double globalObjective(List<Track> ordered, CurveTemplate curve, String mode) {
        double score = 0.0;
        for (int i = 0; i < ordered.size(); i++) {
            score += slotFitScore(ordered.get(i), i, curve, mode);
            if (i < ordered.size() - 1) {
                score += transitionWeight(mode) * pairSmoothnessScore(ordered.get(i), ordered.get(i + 1), mode);
            }
        }
        return score;
    }

    private double pairSmoothnessScore(Track from, Track to, String mode) {
        double transition = scoringService.scoreTransition(from, to).getTotalScore();
        double energyDelta = Math.abs(trackEnergy10(to) - trackEnergy10(from));
        double tensionDelta = Math.abs(trackTension10(to) - trackTension10(from));
        double intensityDelta = Math.abs(trackIntensity10(to) - trackIntensity10(from));

        // Penalize abrupt jumps for smoother flow, but keep rave looser.
        double deltaPenalty;
        if (MODE_RAVE.equals(mode)) {
            deltaPenalty = (energyDelta * 0.10) + (tensionDelta * 0.08) + (intensityDelta * 0.08);
        } else if (MODE_HOUSE_PARTY.equals(mode)) {
            deltaPenalty = (energyDelta * 0.25) + (tensionDelta * 0.20) + (intensityDelta * 0.20);
        } else {
            deltaPenalty = (energyDelta * 0.22) + (tensionDelta * 0.18) + (intensityDelta * 0.16);
        }
        return transition - deltaPenalty;
    }

    private List<SetShapeSlotDebug> buildDebug(List<Track> ordered, CurveTemplate curve, String mode) {
        List<SetShapeSlotDebug> out = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            Track t = ordered.get(i);
            double energy = trackEnergy10(t);
            double tension = trackTension10(t);
            double intensity = trackIntensity10(t);
            double energyDistance = Math.abs(energy - curve.targetEnergy[i]);
            double tensionDistance = Math.abs(tension - curve.targetTension[i]);
            double intensityDistance = Math.abs(intensity - curve.targetIntensity[i]);
            double slotFit = slotFitScore(t, i, curve, mode);
            double inScore = i == 0 ? 0.0 : scoringService.scoreTransition(ordered.get(i - 1), t).getTotalScore();
            double outScore = i == ordered.size() - 1 ? 0.0 : scoringService.scoreTransition(t, ordered.get(i + 1)).getTotalScore();
            String reason = String.format(
                    Locale.US,
                    "slot=%d fit=%.3f energyDist=%.3f tensionDist=%.3f intensityDist=%.3f mode=%s",
                    i, slotFit, energyDistance, tensionDistance, intensityDistance, mode
            );
            out.add(new SetShapeSlotDebug(
                    i,
                    t.getId(),
                    t.getTitle(),
                    curve.targetEnergy[i],
                    curve.targetTension[i],
                    curve.targetIntensity[i],
                    energyDistance,
                    tensionDistance,
                    intensityDistance,
                    slotFit,
                    inScore,
                    outScore,
                    reason
            ));
        }
        return out;
    }

    private double slotFitScore(Track track, int slot, CurveTemplate curve, String mode) {
        double energyDistance = Math.abs(trackEnergy10(track) - curve.targetEnergy[slot]);
        double tensionDistance = Math.abs(trackTension10(track) - curve.targetTension[slot]);
        double intensityDistance = Math.abs(trackIntensity10(track) - curve.targetIntensity[slot]);
        double weightedDistance = (energyWeight(mode) * energyDistance)
                + (tensionWeight(mode) * tensionDistance)
                + (intensityWeight(mode) * intensityDistance);

        double modePenalty = modeSpecificPenalty(track, slot, curve, mode);
        return 10.0 - weightedDistance - modePenalty;
    }

    private double modeSpecificPenalty(Track track, int slot, CurveTemplate curve, String mode) {
        double intensity = trackIntensity10(track);
        if (MODE_HOUSE_PARTY.equals(mode)) {
            // Prevent early intensity spikes; keep a social pacing.
            if (slot < Math.max(2, curve.targetEnergy.length / 3) && intensity >= 7.0) {
                return 1.25;
            }
            return Math.max(0.0, intensity - 7.5) * 0.2;
        }
        if (MODE_CLUB.equals(mode)) {
            // Reward gentle progression; discourage over-contrasty jolts.
            return Math.max(0.0, Math.abs(intensity - curve.targetIntensity[slot]) - 2.0) * 0.15;
        }
        // Rave allows stronger contrast.
        return 0.0;
    }

    private double trackEnergy10(Track t) {
        if (t.getDjEnergy() > 0.0) return clamp10(t.getDjEnergy());
        return clamp10(t.getEnergy() * 10.0);
    }

    private double trackTension10(Track t) {
        return clamp10(t.getTension());
    }

    private double trackIntensity10(Track t) {
        return clamp10(t.getIntensity());
    }

    private double energyWeight(String mode) {
        return MODE_RAVE.equals(mode) ? 0.95 : 1.10;
    }

    private double tensionWeight(String mode) {
        return MODE_RAVE.equals(mode) ? 1.15 : 1.00;
    }

    private double intensityWeight(String mode) {
        if (MODE_HOUSE_PARTY.equals(mode)) return 1.10;
        if (MODE_RAVE.equals(mode)) return 0.95;
        return 1.00;
    }

    private double transitionWeight(String mode) {
        if (MODE_RAVE.equals(mode)) return 0.12;
        if (MODE_HOUSE_PARTY.equals(mode)) return 0.20;
        return 0.18;
    }

    private int compareTrackId(Track a, Track b) {
        String aId = a.getId() == null ? "" : a.getId();
        String bId = b.getId() == null ? "" : b.getId();
        return aId.compareTo(bId);
    }

    private List<Double> toList(double[] values) {
        List<Double> out = new ArrayList<>(values.length);
        for (double value : values) {
            out.add(value);
        }
        return out;
    }

    private double clamp10(double v) {
        return Math.max(0.0, Math.min(10.0, v));
    }

    private void swap(List<Track> list, int i, int j) {
        Track tmp = list.get(i);
        list.set(i, list.get(j));
        list.set(j, tmp);
    }

    private static final class CurveTemplate {
        private final double[] targetEnergy;
        private final double[] targetTension;
        private final double[] targetIntensity;

        private CurveTemplate(double[] targetEnergy, double[] targetTension, double[] targetIntensity) {
            this.targetEnergy = targetEnergy;
            this.targetTension = targetTension;
            this.targetIntensity = targetIntensity;
        }
    }
}
