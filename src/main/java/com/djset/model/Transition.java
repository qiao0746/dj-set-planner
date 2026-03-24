package com.djset.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Transition {
    private final String fromTrackId;
    private final String toTrackId;
    private final double compatibilityScore;
    private final List<String> reasons;
    private final String mixSuggestion;

    public Transition(
            String fromTrackId,
            String toTrackId,
            double compatibilityScore,
            List<String> reasons,
            String mixSuggestion
    ) {
        this.fromTrackId = fromTrackId;
        this.toTrackId = toTrackId;
        this.compatibilityScore = compatibilityScore;
        this.reasons = toUnmodifiableList(reasons);
        this.mixSuggestion = mixSuggestion;
    }

    private static List<String> toUnmodifiableList(List<String> input) {
        return input == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(input));
    }

    public String getFromTrackId() {
        return fromTrackId;
    }

    public String getToTrackId() {
        return toTrackId;
    }

    public double getCompatibilityScore() {
        return compatibilityScore;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public String getMixSuggestion() {
        return mixSuggestion;
    }

    @Override
    public String toString() {
        return "Transition{" +
                "fromTrackId='" + fromTrackId + '\'' +
                ", toTrackId='" + toTrackId + '\'' +
                ", compatibilityScore=" + compatibilityScore +
                ", reasons=" + reasons +
                ", mixSuggestion='" + mixSuggestion + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transition that)) return false;
        return Double.compare(that.compatibilityScore, compatibilityScore) == 0
                && Objects.equals(fromTrackId, that.fromTrackId)
                && Objects.equals(toTrackId, that.toTrackId)
                && Objects.equals(reasons, that.reasons)
                && Objects.equals(mixSuggestion, that.mixSuggestion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromTrackId, toTrackId, compatibilityScore, reasons, mixSuggestion);
    }
}
