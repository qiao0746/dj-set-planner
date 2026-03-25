package com.djset.service;

import com.djset.PlanLimits;
import com.djset.model.SetPlan;
import com.djset.model.Track;
import com.djset.model.Transition;
import com.djset.scorer.TransitionScoreResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SetPlannerService {
    private static final int MAX_RECOMMENDATIONS = PlanLimits.MAX_SET_SIZE;
    private static final int MIN_RECOMMENDATIONS = PlanLimits.MIN_SET_SIZE;
    private static final double DEFAULT_OVERALL_SCORE = 0.0;
    private static final double MIN_ACCEPTABLE_TRANSITION_SCORE = 1.0;

    private final ScoringService scoringService;

    public SetPlannerService() {
        this(new ScoringService());
    }

    public SetPlannerService(ScoringService scoringService) {
        this.scoringService = scoringService;
    }

    public SetPlan createPlan(List<Track> inputTracks) {
        return createPlan(inputTracks, null, null);
    }

    public SetPlan createPlan(List<Track> inputTracks, String style, String targetCurve) {
        return createPlan(inputTracks, style, targetCurve, null);
    }

    public SetPlan createPlan(List<Track> inputTracks, String style, String targetCurve, Integer requestedCount) {
        validateInputTracks(inputTracks);
        int targetCount = resolveTargetCount(inputTracks.size(), requestedCount);

        List<Track> remaining = new ArrayList<>(inputTracks);
        List<Track> orderedTracks = new ArrayList<>();
        List<Transition> transitions = new ArrayList<>();

        // Step 1: choose an opening track with lower energy.
        Track opening = chooseOpeningTrack(remaining);
        orderedTracks.add(opening);
        remaining.remove(opening);

        if (targetCount == 1) {
            return new SetPlan(style, targetCurve, orderedTracks, transitions, DEFAULT_OVERALL_SCORE);
        }

        double transitionScoreSum = DEFAULT_OVERALL_SCORE;

        // Steps 2 & 3: greedily choose the best next transition until no tracks remain
        // or until the requested recommendation count is reached.
        while (!remaining.isEmpty() && orderedTracks.size() < targetCount) {
            Track from = orderedTracks.get(orderedTracks.size() - 1);
            TransitionCandidate candidate = chooseBestNext(from, remaining);
            if (shouldStopForLowRelevance(candidate)) {
                break;
            }

            orderedTracks.add(candidate.toTrack);
            remaining.remove(candidate.toTrack);
            transitions.add(new Transition(
                    from.getId(),
                    candidate.toTrack.getId(),
                    candidate.scoreResult.getTotalScore(),
                    candidate.scoreResult.getReasons(),
                    MixSuggestion.fromTransitionScore(candidate.scoreResult)
            ));
            transitionScoreSum += candidate.scoreResult.getTotalScore();
        }

        // Step 4: compute overall score as average transition score.
        double overallScore = transitions.isEmpty() ? DEFAULT_OVERALL_SCORE : (transitionScoreSum / transitions.size());
        return new SetPlan(style, targetCurve, orderedTracks, transitions, overallScore);
    }

    public SetPlan createPlanFromFirstTrack(
            List<Track> inputTracks,
            String firstTrackId,
            String style,
            String targetCurve,
            Integer requestedCount
    ) {
        validateInputTracks(inputTracks);
        if (firstTrackId == null || firstTrackId.isBlank()) {
            throw new IllegalArgumentException("First track id is required.");
        }
        int targetCount = resolveTargetCount(inputTracks.size(), requestedCount);

        List<Track> remaining = new ArrayList<>(inputTracks);
        List<Track> orderedTracks = new ArrayList<>();
        List<Transition> transitions = new ArrayList<>();

        Track opening = findTrackById(remaining, firstTrackId);
        orderedTracks.add(opening);
        remaining.remove(opening);

        if (targetCount == 1) {
            return new SetPlan(style, targetCurve, orderedTracks, transitions, DEFAULT_OVERALL_SCORE);
        }

        double transitionScoreSum = DEFAULT_OVERALL_SCORE;
        while (!remaining.isEmpty() && orderedTracks.size() < targetCount) {
            Track from = orderedTracks.get(orderedTracks.size() - 1);
            TransitionCandidate candidate = chooseBestNext(from, remaining);
            if (shouldStopForLowRelevance(candidate)) {
                break;
            }

            orderedTracks.add(candidate.toTrack);
            remaining.remove(candidate.toTrack);
            transitions.add(new Transition(
                    from.getId(),
                    candidate.toTrack.getId(),
                    candidate.scoreResult.getTotalScore(),
                    candidate.scoreResult.getReasons(),
                    MixSuggestion.fromTransitionScore(candidate.scoreResult)
            ));
            transitionScoreSum += candidate.scoreResult.getTotalScore();
        }

        double overallScore = transitions.isEmpty() ? DEFAULT_OVERALL_SCORE : (transitionScoreSum / transitions.size());
        return new SetPlan(style, targetCurve, orderedTracks, transitions, overallScore);
    }

    private void validateInputTracks(List<Track> inputTracks) {
        if (inputTracks == null || inputTracks.isEmpty()) {
            throw new IllegalArgumentException("Track list cannot be empty.");
        }
    }

    private Track chooseOpeningTrack(List<Track> tracks) {
        return tracks.stream()
                .min(Comparator
                        .comparingDouble(Track::getEnergy)
                        .thenComparingDouble(Track::getBpm)
                        .thenComparing(Track::getId, Comparator.nullsLast(String::compareTo)))
                .orElseThrow(() -> new IllegalArgumentException("No tracks available."));
    }

    private Track findTrackById(List<Track> tracks, String id) {
        return tracks.stream()
                .filter(t -> id.equals(t.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("First track id not found: " + id));
    }

    private TransitionCandidate chooseBestNext(Track from, List<Track> candidates) {
        TransitionCandidate best = null;
        for (Track candidate : candidates) {
            TransitionCandidate current = scoreCandidate(from, candidate);
            if (best == null || compareCandidate(current, best, from) > 0) {
                best = current;
            }
        }
        if (best == null) {
            throw new IllegalStateException("Could not choose next track.");
        }
        return best;
    }

    private TransitionCandidate scoreCandidate(Track from, Track candidate) {
        TransitionScoreResult score = scoringService.scoreTransition(from, candidate);
        return new TransitionCandidate(candidate, score);
    }

    private int resolveTargetCount(int availableTracks, Integer requestedCount) {
        if (requestedCount == null) {
            return Math.min(availableTracks, MAX_RECOMMENDATIONS);
        }
        if (requestedCount < MIN_RECOMMENDATIONS || requestedCount > MAX_RECOMMENDATIONS) {
            throw new IllegalArgumentException(
                    "Requested count must be between "
                            + MIN_RECOMMENDATIONS
                            + " and "
                            + MAX_RECOMMENDATIONS
                            + ".");
        }
        return Math.min(availableTracks, requestedCount);
    }

    private boolean shouldStopForLowRelevance(TransitionCandidate candidate) {
        return candidate.scoreResult.getTotalScore() < MIN_ACCEPTABLE_TRANSITION_SCORE;
    }

    private int compareCandidate(TransitionCandidate a, TransitionCandidate b, Track from) {
        int byTotal = Double.compare(a.scoreResult.getTotalScore(), b.scoreResult.getTotalScore());
        if (byTotal != 0) return byTotal;

        // Deterministic tie-breakers: smaller BPM diff, then stable id ordering.
        double aDiff = Math.abs(from.getBpm() - a.toTrack.getBpm());
        double bDiff = Math.abs(from.getBpm() - b.toTrack.getBpm());
        int byBpmDiff = Double.compare(bDiff, aDiff);
        if (byBpmDiff != 0) return byBpmDiff;

        String aId = a.toTrack.getId() == null ? "" : a.toTrack.getId();
        String bId = b.toTrack.getId() == null ? "" : b.toTrack.getId();
        return aId.compareTo(bId);
    }

    private static final class TransitionCandidate {
        private final Track toTrack;
        private final TransitionScoreResult scoreResult;

        private TransitionCandidate(Track toTrack, TransitionScoreResult scoreResult) {
            this.toTrack = toTrack;
            this.scoreResult = scoreResult;
        }
    }
}
