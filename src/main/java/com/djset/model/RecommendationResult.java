package com.djset.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RecommendationResult {
    private final String currentTrackId;
    private final List<TrackRecommendation> recommendations;

    public RecommendationResult(String currentTrackId, List<TrackRecommendation> recommendations) {
        this.currentTrackId = currentTrackId;
        this.recommendations = recommendations == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(recommendations));
    }

    public String getCurrentTrackId() {
        return currentTrackId;
    }

    public List<TrackRecommendation> getRecommendations() {
        return recommendations;
    }

    @Override
    public String toString() {
        return "RecommendationResult{" +
                "currentTrackId='" + currentTrackId + '\'' +
                ", recommendations=" + recommendations +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecommendationResult that)) return false;
        return Objects.equals(currentTrackId, that.currentTrackId)
                && Objects.equals(recommendations, that.recommendations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currentTrackId, recommendations);
    }
}
