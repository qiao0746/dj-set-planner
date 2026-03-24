package com.djset.service;

import com.djset.model.SetShapePlan;
import com.djset.model.Track;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetShapeServiceTest {
    private final SetShapeService setShapeService = new SetShapeService();

    @Test
    void shapeSetProducesDeterministicOrderingForClub() {
        List<Track> tracks = sampleTracks();

        SetShapePlan first = setShapeService.shapeSet(tracks, "club");
        SetShapePlan second = setShapeService.shapeSet(tracks, "club");

        assertEquals(first.getOrderedTracks(), second.getOrderedTracks());
        assertEquals(5, first.getTargetEnergyCurve().size());
        assertEquals(5, first.getTargetTensionCurve().size());
        assertEquals(5, first.getSlotDebug().size());
    }

    @Test
    void shapeSetRejectsUnsupportedMode() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> setShapeService.shapeSet(sampleTracks(), "festival")
        );
        assertTrue(ex.getMessage().contains("club, rave, house_party"));
    }

    @Test
    void raveCurveContainsWaves() {
        SetShapePlan plan = setShapeService.shapeSet(sampleTracks(), "rave");
        List<Double> energy = plan.getTargetEnergyCurve();
        // For wave-like behavior in this deterministic template.
        assertTrue(energy.get(1) > energy.get(0) || energy.get(2) < energy.get(1));
    }

    @Test
    void pinFirstTrackKeepsOpenerAtSlotZero() {
        List<Track> tracks = sampleTracks();
        String openerId = tracks.get(0).getId();
        SetShapePlan plan = setShapeService.shapeSet(tracks, "club", true);
        assertEquals(openerId, plan.getOrderedTracks().get(0).getId());
    }

    private List<Track> sampleTracks() {
        return List.of(
                track("t1", 120, 0.45, 4.5, 3.5, 3.0),
                track("t2", 124, 0.52, 5.3, 4.1, 4.0),
                track("t3", 128, 0.61, 6.4, 5.0, 6.2),
                track("t4", 132, 0.70, 7.1, 6.3, 7.0),
                track("t5", 136, 0.78, 8.0, 7.2, 8.3)
        );
    }

    private Track track(String id, double bpm, double energy, double djEnergy, double intensity, double tension) {
        return new Track(
                id,
                id,
                "artist",
                bpm,
                1.0,
                "8A",
                1.0,
                energy,
                0.5,
                djEnergy,
                intensity,
                tension,
                Map.of(),
                List.of("house"),
                List.of(),
                180.0
        );
    }
}
