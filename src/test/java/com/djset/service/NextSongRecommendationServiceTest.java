package com.djset.service;

import com.djset.model.NextSongIntent;
import com.djset.model.NextSongRecommendationItem;
import com.djset.model.Track;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NextSongRecommendationServiceTest {

    private final NextSongRecommendationService service = new NextSongRecommendationService();

    @Test
    void upliftPrefersHigherEnergyWhenTransitionSimilar() {
        Track current = full("c", 120, "8A", 0.5, 0.7, 0.7);
        Track low = full("low", 120, "8A", 0.45, 0.6, 0.6);
        Track high = full("high", 120, "8A", 0.85, 0.8, 0.8);

        List<NextSongRecommendationItem> up = service.recommend(current, List.of(low, high), NextSongIntent.UPLIFT, 1);
        assertEquals("high", up.get(0).getTrack().getId());

        List<NextSongRecommendationItem> ch = service.recommend(current, List.of(low, high), NextSongIntent.CHILL, 1);
        assertEquals("low", ch.get(0).getTrack().getId());
    }

    @Test
    void groovyPrefersCloserEnergy() {
        Track current = full("c", 120, "8A", 0.5, 0.5, 0.5);
        Track close = full("n1", 120, "8A", 0.52, 0.5, 0.5);
        Track far = full("n2", 120, "8A", 0.95, 0.5, 0.5);

        List<NextSongRecommendationItem> g = service.recommend(current, List.of(far, close), NextSongIntent.GROOVY, 1);
        assertEquals("n1", g.get(0).getTrack().getId());
    }

    @Test
    void returnsFewerThanNWhenPoolSmall() {
        Track current = full("c", 120, "8A", 0.5, 0.5, 0.5);
        Track a = full("a", 121, "9A", 0.55, 0.5, 0.5);
        List<NextSongRecommendationItem> list = service.recommend(current, List.of(a), NextSongIntent.GROOVY, 20);
        assertEquals(1, list.size());
    }

    @Test
    void skipsCurrentIdInCandidateList() {
        Track current = full("c", 120, "8A", 0.5, 0.5, 0.5);
        List<NextSongRecommendationItem> list = service.recommend(
                current,
                List.of(current, full("a", 121, "9A", 0.55, 0.5, 0.5)),
                NextSongIntent.GROOVY,
                5);
        assertEquals(1, list.size());
        assertEquals("a", list.get(0).getTrack().getId());
    }

    @Test
    void rejectsBadN() {
        Track current = full("c", 120, "8A", 0.5, 0.5, 0.5);
        assertThrows(IllegalArgumentException.class,
                () -> service.recommend(current, List.of(full("a", 121, "9A", 0.55, 0.5, 0.5)), NextSongIntent.GROOVY, 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.recommend(current, List.of(full("a", 121, "9A", 0.55, 0.5, 0.5)), NextSongIntent.GROOVY, 21));
    }

    @Test
    void explanationNonBlank() {
        Track current = full("c", 120, "8A", 0.5, 0.5, 0.5);
        List<NextSongRecommendationItem> list = service.recommend(
                current,
                List.of(full("a", 121, "9A", 0.55, 0.5, 0.5)),
                NextSongIntent.UPLIFT,
                1);
        assertTrue(list.get(0).getExplanation().length() > 10);
        assertTrue(list.get(0).getMixSuggestion().length() > 5);
    }

    private static Track full(String id, double bpm, String key, double energy, double intensity, double tension) {
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
                energy,
                intensity,
                tension,
                Map.of(),
                List.of("house"),
                List.of("groovy"),
                300.0
        );
    }
}
