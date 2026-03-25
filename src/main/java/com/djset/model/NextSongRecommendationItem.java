package com.djset.model;

import java.util.Objects;

/**
 * One ranked next-track suggestion from {@link com.djset.service.NextSongRecommendationService}.
 */
public class NextSongRecommendationItem {
    private final Track track;
    private final double finalScore;
    private final String explanation;
    /** Blend hint from base transition score (same bands as planner {@code Transition#mixSuggestion}). */
    private final String mixSuggestion;

    public NextSongRecommendationItem(Track track, double finalScore, String explanation, String mixSuggestion) {
        this.track = Objects.requireNonNull(track, "track");
        this.finalScore = finalScore;
        this.explanation = explanation == null ? "" : explanation;
        this.mixSuggestion = mixSuggestion == null ? "" : mixSuggestion;
    }

    public Track getTrack() {
        return track;
    }

    public double getFinalScore() {
        return finalScore;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getMixSuggestion() {
        return mixSuggestion;
    }
}
