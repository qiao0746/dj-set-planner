package com.djset.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TrackRecommendation {
    private final Track track;
    private final double score;
    private final List<String> reasons;
    private final String mixSuggestion;

    public TrackRecommendation(Track track, double score, List<String> reasons, String mixSuggestion) {
        this.track = track;
        this.score = score;
        this.reasons = reasons == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(reasons));
        this.mixSuggestion = mixSuggestion;
    }

    public Track getTrack() {
        return track;
    }

    public double getScore() {
        return score;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public String getMixSuggestion() {
        return mixSuggestion;
    }

    @Override
    public String toString() {
        return "TrackRecommendation{" +
                "track=" + track +
                ", score=" + score +
                ", reasons=" + reasons +
                ", mixSuggestion='" + mixSuggestion + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrackRecommendation that)) return false;
        return Double.compare(that.score, score) == 0
                && Objects.equals(track, that.track)
                && Objects.equals(reasons, that.reasons)
                && Objects.equals(mixSuggestion, that.mixSuggestion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(track, score, reasons, mixSuggestion);
    }
}
