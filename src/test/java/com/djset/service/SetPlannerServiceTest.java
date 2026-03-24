package com.djset.service;

import com.djset.model.SetPlan;
import com.djset.model.Track;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetPlannerServiceTest {

    private final SetPlannerService planner = new SetPlannerService();

    @Test
    void createPlanRespectsRequestedCountWhenEnoughRelevantTracks() {
        List<Track> tracks = List.of(
                track("t1", 122, "8A", 0.30, List.of("house"), List.of("warm")),
                track("t2", 123, "9A", 0.40, List.of("house"), List.of("groovy")),
                track("t3", 124, "9B", 0.52, List.of("house"), List.of("peak"))
        );

        SetPlan plan = planner.createPlan(tracks, "house", "rise", 2);

        assertEquals(2, plan.getOrderedTracks().size());
        assertEquals(1, plan.getTransitions().size());
    }

    @Test
    void createPlanStopsEarlyWhenRemainingTracksAreTooIrrelevant() {
        List<Track> tracks = List.of(
                track("open", 120, "1A", 0.10, List.of("house"), List.of("warm")),
                track("good", 121, "2A", 0.20, List.of("house"), List.of("warm")),
                track("bad", 160, "10B", 0.90, List.of("dnb"), List.of("chaotic"))
        );

        SetPlan plan = planner.createPlan(tracks, "house", "rise", 3);

        assertEquals(2, plan.getOrderedTracks().size());
        assertEquals(1, plan.getTransitions().size());
        assertTrue(plan.getOverallScore() >= 1.0);
    }

    @Test
    void createPlanRejectsRequestedCountOutsideRange() {
        List<Track> tracks = List.of(
                track("t1", 122, "8A", 0.30, List.of("house"), List.of("warm")),
                track("t2", 123, "9A", 0.40, List.of("house"), List.of("groovy"))
        );

        IllegalArgumentException exLow = assertThrows(
                IllegalArgumentException.class,
                () -> planner.createPlan(tracks, "house", "rise", 0)
        );
        assertTrue(exLow.getMessage().contains("between 1 and 10"));

        IllegalArgumentException exHigh = assertThrows(
                IllegalArgumentException.class,
                () -> planner.createPlan(tracks, "house", "rise", 11)
        );
        assertTrue(exHigh.getMessage().contains("between 1 and 10"));
    }

    @Test
    void createPlanFromFirstTrackKeepsRequestedStartTrack() {
        List<Track> tracks = List.of(
                track("t1", 122, "8A", 0.30, List.of("house"), List.of("warm")),
                track("t2", 123, "9A", 0.40, List.of("house"), List.of("groovy")),
                track("t3", 124, "9B", 0.52, List.of("house"), List.of("peak"))
        );

        SetPlan plan = planner.createPlanFromFirstTrack(tracks, "t2", "house", "rise", 3);

        assertEquals("t2", plan.getOrderedTracks().get(0).getId());
        assertTrue(plan.getOrderedTracks().size() <= 3);
    }

    private static Track track(
            String id,
            double bpm,
            String key,
            double energy,
            List<String> genreTags,
            List<String> vibeTags
    ) {
        return new Track(
                id,
                id,
                "artist",
                bpm,
                1.0,
                key,
                1.0,
                energy,
                0.6,
                genreTags,
                vibeTags,
                300
        );
    }
}
