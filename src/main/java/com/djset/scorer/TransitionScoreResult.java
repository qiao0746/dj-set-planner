package com.djset.scorer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TransitionScoreResult {
    private final double totalScore;
    private final double bpmScore;
    private final double keyScore;
    private final double energyScore;
    private final double styleScore;
    private final List<String> reasons;

    public TransitionScoreResult(
            double totalScore,
            double bpmScore,
            double keyScore,
            double energyScore,
            double styleScore,
            List<String> reasons
    ) {
        this.totalScore = totalScore;
        this.bpmScore = bpmScore;
        this.keyScore = keyScore;
        this.energyScore = energyScore;
        this.styleScore = styleScore;
        this.reasons = toUnmodifiableList(reasons);
    }

    private static List<String> toUnmodifiableList(List<String> input) {
        return input == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(input));
    }

    public double getTotalScore() {
        return totalScore;
    }

    public double getBpmScore() {
        return bpmScore;
    }

    public double getKeyScore() {
        return keyScore;
    }

    public double getEnergyScore() {
        return energyScore;
    }

    public double getStyleScore() {
        return styleScore;
    }

    public List<String> getReasons() {
        return reasons;
    }

    @Override
    public String toString() {
        return "TransitionScoreResult{" +
                "totalScore=" + totalScore +
                ", bpmScore=" + bpmScore +
                ", keyScore=" + keyScore +
                ", energyScore=" + energyScore +
                ", styleScore=" + styleScore +
                ", reasons=" + reasons +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransitionScoreResult that)) return false;
        return Double.compare(that.totalScore, totalScore) == 0
                && Double.compare(that.bpmScore, bpmScore) == 0
                && Double.compare(that.keyScore, keyScore) == 0
                && Double.compare(that.energyScore, energyScore) == 0
                && Double.compare(that.styleScore, styleScore) == 0
                && Objects.equals(reasons, that.reasons);
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalScore, bpmScore, keyScore, energyScore, styleScore, reasons);
    }
}
