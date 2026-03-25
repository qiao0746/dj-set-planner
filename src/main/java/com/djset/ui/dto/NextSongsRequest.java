package com.djset.ui.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * JSON body for {@code POST /api/next-songs}: analyze folder, then rank candidates after a current track.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NextSongsRequest {
    public String musicDir;
    /** Partial title match (same rules as opening track in the planner). */
    public String currentTrackTitle;
    /**
     * Optional partial titles; each entry can match multiple tracks.
     * When null or empty, every analyzed track except the current one is a candidate.
     */
    public List<String> candidateTrackTitles;
    /** {@code uplift}, {@code chill}, or {@code groovy}. */
    public String intent;
    /** Default 5; max 20. */
    public Integer numberOfRecommendations;

    public String analyzerMode = "standard";
    public String pythonCmd = "python";
    public String analyzerScript = "python/audio_analyzer.py";
    public int analyzeWorkers = 4;
    public String defaultGenre;
    public Boolean reanalyze;
}
