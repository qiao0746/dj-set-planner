package com.djset.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SetShapePlan {
    private final String mode;
    private final List<Double> targetEnergyCurve;
    private final List<Double> targetTensionCurve;
    private final List<Track> orderedTracks;
    private final List<SetShapeSlotDebug> slotDebug;

    public SetShapePlan(
            String mode,
            List<Double> targetEnergyCurve,
            List<Double> targetTensionCurve,
            List<Track> orderedTracks,
            List<SetShapeSlotDebug> slotDebug
    ) {
        this.mode = mode;
        this.targetEnergyCurve = toUnmodifiableList(targetEnergyCurve);
        this.targetTensionCurve = toUnmodifiableList(targetTensionCurve);
        this.orderedTracks = toUnmodifiableList(orderedTracks);
        this.slotDebug = toUnmodifiableList(slotDebug);
    }

    private static <T> List<T> toUnmodifiableList(List<T> values) {
        return values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
    }

    public String getMode() {
        return mode;
    }

    public List<Double> getTargetEnergyCurve() {
        return targetEnergyCurve;
    }

    public List<Double> getTargetTensionCurve() {
        return targetTensionCurve;
    }

    public List<Track> getOrderedTracks() {
        return orderedTracks;
    }

    public List<SetShapeSlotDebug> getSlotDebug() {
        return slotDebug;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SetShapePlan that)) return false;
        return Objects.equals(mode, that.mode)
                && Objects.equals(targetEnergyCurve, that.targetEnergyCurve)
                && Objects.equals(targetTensionCurve, that.targetTensionCurve)
                && Objects.equals(orderedTracks, that.orderedTracks)
                && Objects.equals(slotDebug, that.slotDebug);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, targetEnergyCurve, targetTensionCurve, orderedTracks, slotDebug);
    }
}
