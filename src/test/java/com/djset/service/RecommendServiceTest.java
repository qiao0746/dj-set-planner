package com.djset.service;

import com.djset.model.RecommendationResult;
import com.djset.model.Track;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecommendServiceTest {
    private final RecommendService service = new RecommendService();

    @Test
    void recommendReturnsTopNWithoutCurrentTrack() {
        List<Track> tracks = List.of(
                track("t1", 122, "8A", 0.30, List.of("house"), List.of("warm")),
                track("t2", 123, "9A", 0.40, List.of("house"), List.of("groovy")),
                track("t3", 130, "4B", 0.90, List.of("dnb"), List.of("hard"))
        );

        RecommendationResult result = service.recommendNextSongs(tracks, "t1", 2);
        assertEquals("t1", result.getCurrentTrackId());
        assertEquals(2, result.getRecommendations().size());
        assertEquals("t2", result.getRecommendations().get(0).getTrack().getId());
    }

    @Test
    void recommendRejectsInvalidCount() {
        List<Track> tracks = List.of(track("t1", 122, "8A", 0.30, List.of("house"), List.of("warm")));
        assertThrows(IllegalArgumentException.class, () -> service.recommendNextSongs(tracks, "t1", 11));
    }

    private static Track track(
            String id,
            double bpm,
            String key,
            double energy,
            List<String> genreTags,
            List<String> vibeTags
    ) {
        return new Track(id, id, "artist", bpm, 1.0, key, 1.0, energy, 0.6, genreTags, vibeTags, 300);
    }
}
