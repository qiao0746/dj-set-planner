package com.djset.service;

import com.djset.model.SetPlan;
import com.djset.model.Track;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuildSetServiceTest {
    private final BuildSetService service = new BuildSetService();

    @Test
    void buildFromSelectedSongsUsesOnlySelectedIds() {
        List<Track> tracks = List.of(
                track("t1", 122, "8A", 0.30),
                track("t2", 123, "9A", 0.40),
                track("t3", 124, "9B", 0.50)
        );

        SetPlan plan = service.buildFromSelectedSongs(tracks, List.of("t1", "t3"), "house", "rise", 2);
        assertEquals(2, plan.getOrderedTracks().size());
    }

    @Test
    void buildFromSelectedSongsRejectsUnknownIds() {
        List<Track> tracks = List.of(track("t1", 122, "8A", 0.30));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.buildFromSelectedSongs(tracks, List.of("missing"), "house", "rise", 1)
        );
    }

    private static Track track(String id, double bpm, String key, double energy) {
        return new Track(id, id, "artist", bpm, 1.0, key, 1.0, energy, 0.6, List.of("house"), List.of("warm"), 300);
    }
}
