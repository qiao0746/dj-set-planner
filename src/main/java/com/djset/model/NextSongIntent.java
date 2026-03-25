package com.djset.model;

import java.util.Locale;

/**
 * Crowd-direction hint for {@link com.djset.service.NextSongRecommendationService}.
 */
public enum NextSongIntent {
    UPLIFT,
    CHILL,
    GROOVY;

    public static NextSongIntent parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("intent is required (uplift, chill, groovy).");
        }
        String t = raw.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "uplift" -> UPLIFT;
            case "chill" -> CHILL;
            case "groovy" -> GROOVY;
            default -> throw new IllegalArgumentException("intent must be uplift, chill, or groovy.");
        };
    }
}
