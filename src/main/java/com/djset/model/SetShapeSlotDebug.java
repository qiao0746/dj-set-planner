package com.djset.model;

import java.util.Objects;

public class SetShapeSlotDebug {
    private final int position;
    private final String trackId;
    private final String trackTitle;
    private final double targetEnergy;
    private final double targetTension;
    private final double targetIntensity;
    private final double energyDistance;
    private final double tensionDistance;
    private final double intensityDistance;
    private final double slotFitScore;
    private final double transitionInScore;
    private final double transitionOutScore;
    private final String reason;

    public SetShapeSlotDebug(
            int position,
            String trackId,
            String trackTitle,
            double targetEnergy,
            double targetTension,
            double targetIntensity,
            double energyDistance,
            double tensionDistance,
            double intensityDistance,
            double slotFitScore,
            double transitionInScore,
            double transitionOutScore,
            String reason
    ) {
        this.position = position;
        this.trackId = trackId;
        this.trackTitle = trackTitle;
        this.targetEnergy = targetEnergy;
        this.targetTension = targetTension;
        this.targetIntensity = targetIntensity;
        this.energyDistance = energyDistance;
        this.tensionDistance = tensionDistance;
        this.intensityDistance = intensityDistance;
        this.slotFitScore = slotFitScore;
        this.transitionInScore = transitionInScore;
        this.transitionOutScore = transitionOutScore;
        this.reason = reason;
    }

    public int getPosition() {
        return position;
    }

    public String getTrackId() {
        return trackId;
    }

    public String getTrackTitle() {
        return trackTitle;
    }

    public double getTargetEnergy() {
        return targetEnergy;
    }

    public double getTargetTension() {
        return targetTension;
    }

    public double getTargetIntensity() {
        return targetIntensity;
    }

    public double getEnergyDistance() {
        return energyDistance;
    }

    public double getTensionDistance() {
        return tensionDistance;
    }

    public double getIntensityDistance() {
        return intensityDistance;
    }

    public double getSlotFitScore() {
        return slotFitScore;
    }

    public double getTransitionInScore() {
        return transitionInScore;
    }

    public double getTransitionOutScore() {
        return transitionOutScore;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SetShapeSlotDebug that)) return false;
        return position == that.position
                && Double.compare(that.targetEnergy, targetEnergy) == 0
                && Double.compare(that.targetTension, targetTension) == 0
                && Double.compare(that.targetIntensity, targetIntensity) == 0
                && Double.compare(that.energyDistance, energyDistance) == 0
                && Double.compare(that.tensionDistance, tensionDistance) == 0
                && Double.compare(that.intensityDistance, intensityDistance) == 0
                && Double.compare(that.slotFitScore, slotFitScore) == 0
                && Double.compare(that.transitionInScore, transitionInScore) == 0
                && Double.compare(that.transitionOutScore, transitionOutScore) == 0
                && Objects.equals(trackId, that.trackId)
                && Objects.equals(trackTitle, that.trackTitle)
                && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                position, trackId, trackTitle, targetEnergy, targetTension, targetIntensity,
                energyDistance, tensionDistance, intensityDistance, slotFitScore,
                transitionInScore, transitionOutScore, reason
        );
    }
}
