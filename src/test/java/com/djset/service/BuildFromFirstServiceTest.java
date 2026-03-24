package com.djset.service;

import com.djset.model.SetPlan;
import com.djset.model.Track;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuildFromFirstServiceTest {
    private final BuildFromFirstService service = new BuildFromFirstService();

    @Test
    void buildFromFirstTrackStartsWithGivenTrack() {
        List<Track> tracks = List.of(
                track("a", 122, "8A", 0.30),
                track("b", 123, "9A", 0.40),
                track("c", 124, "9B", 0.50)
        );

        SetPlan plan = service.buildFromFirstTrack(tracks, "b", "house", "rise", 3);
        assertEquals("b", plan.getOrderedTracks().get(0).getId());
    }

    @Test
    void buildFromFirstTrackRejectsUnknownStartTrack() {
        List<Track> tracks = List.of(track("a", 122, "8A", 0.30));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.buildFromFirstTrack(tracks, "missing", "house", "rise", 1)
        );
    }

    @Test
    void buildFromFirstTrackTitleMatchesCaseInsensitivePartial() {
        List<Track> tracks = List.of(
                track("a", 122, "8A", 0.30, "Odd Mob, OMNOM, HYPERBEAM - Coming Up (It's Dare)"),
                track("b", 123, "9A", 0.40, "Dom Dolla - Miracle Maker")
        );

        SetPlan plan = service.buildFromFirstTrackTitle(tracks, "coming up", "house", "rise", 2);
        assertEquals("a", plan.getOrderedTracks().get(0).getId());
    }

    @Test
    void buildFromFirstTrackTitleRejectsWhenNoMatch() {
        List<Track> tracks = List.of(track("a", 122, "8A", 0.30));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.buildFromFirstTrackTitle(tracks, "not-found", "house", "rise", 1)
        );
    }

    private static Track track(String id, double bpm, String key, double energy) {
        return track(id, bpm, key, energy, id);
    }

    private static Track track(String id, double bpm, String key, double energy, String title) {
        return new Track(id, title, "artist", bpm, 1.0, key, 1.0, energy, 0.6, List.of("house"), List.of("warm"), 300);
    }
}
