package com.djset.service;

import com.djset.PlanLimits;
import com.djset.model.RecommendationResult;
import com.djset.model.Track;
import com.djset.model.TrackRecommendation;
import com.djset.scorer.TransitionScoreResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RecommendService {
    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 10;

    private final ScoringService scoringService = new ScoringService();

    public RecommendationResult recommendNextSongs(List<Track> tracks, String currentTrackId, int count) {
        validate(tracks, currentTrackId, count);
        Track current = findCurrentTrack(tracks, currentTrackId);

        List<TrackRecommendation> recs = new ArrayList<>();
        for (Track candidate : tracks) {
            if (candidate.getId() != null && candidate.getId().equals(currentTrackId)) {
                continue;
            }
            TransitionScoreResult score = scoringService.scoreTransition(current, candidate);
            recs.add(new TrackRecommendation(
                    candidate,
                    score.getTotalScore(),
                    score.getReasons(),
                    toMixSuggestion(score.getTotalScore())
            ));
        }

        recs.sort(Comparator
                .comparingDouble(TrackRecommendation::getScore)
                .reversed()
                .thenComparing(r -> r.getTrack().getId(), Comparator.nullsLast(String::compareTo)));

        int limit = Math.min(count, recs.size());
        return new RecommendationResult(currentTrackId, recs.subList(0, limit));
    }

    private void validate(List<Track> tracks, String currentTrackId, int count) {
        if (tracks == null || tracks.isEmpty()) {
            throw new IllegalArgumentException("Track list cannot be empty.");
        }
        if (currentTrackId == null || currentTrackId.isBlank()) {
            throw new IllegalArgumentException("Current track id is required.");
        }
        if (count < MIN_COUNT || count > MAX_COUNT) {
            throw new IllegalArgumentException(
                    "Count must be between " + MIN_COUNT + " and " + MAX_COUNT + ".");
        }
    }

    private Track findCurrentTrack(List<Track> tracks, String currentTrackId) {
        return tracks.stream()
                .filter(t -> currentTrackId.equals(t.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Current track id not found: " + currentTrackId));
    }

    private String toMixSuggestion(double score) {
        if (score >= 6.0) return "Long blend, phrased 16-32 bars.";
        if (score >= 3.0) return "Standard blend, 8-16 bars.";
        return "Quick transition recommended.";
    }
}
