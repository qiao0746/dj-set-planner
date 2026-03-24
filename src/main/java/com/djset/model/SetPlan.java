package com.djset.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SetPlan {
    private final String style;
    private final String targetCurve;
    private final List<Track> orderedTracks;
    private final List<Transition> transitions;
    private final double overallScore;

    public SetPlan(
            String style,
            String targetCurve,
            List<Track> orderedTracks,
            List<Transition> transitions,
            double overallScore
    ) {
        this.style = style;
        this.targetCurve = targetCurve;
        this.orderedTracks = toUnmodifiableList(orderedTracks);
        this.transitions = toUnmodifiableList(transitions);
        this.overallScore = overallScore;
    }

    private static <T> List<T> toUnmodifiableList(List<T> input) {
        return input == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(input));
    }

    public String getStyle() {
        return style;
    }

    public String getTargetCurve() {
        return targetCurve;
    }

    public List<Track> getOrderedTracks() {
        return orderedTracks;
    }

    public List<Transition> getTransitions() {
        return transitions;
    }

    public double getOverallScore() {
        return overallScore;
    }

    @Override
    public String toString() {
        return "SetPlan{" +
                "style='" + style + '\'' +
                ", targetCurve='" + targetCurve + '\'' +
                ", orderedTracks=" + orderedTracks +
                ", transitions=" + transitions +
                ", overallScore=" + overallScore +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SetPlan setPlan)) return false;
        return Double.compare(setPlan.overallScore, overallScore) == 0
                && Objects.equals(style, setPlan.style)
                && Objects.equals(targetCurve, setPlan.targetCurve)
                && Objects.equals(orderedTracks, setPlan.orderedTracks)
                && Objects.equals(transitions, setPlan.transitions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(style, targetCurve, orderedTracks, transitions, overallScore);
    }
}
