package com.djset.ui.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * JSON body for {@code POST /api/run} (interactive web UI).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanRunRequest {
    public String musicDir;
    /**
     * {@code default} — build-from-first: opener fixed, next tracks by transition scoring (default).
     * {@code shaped} — set-shape curves (see shapeModes), optional pin when firstTrackTitle is set.
     * Legacy alias: {@code greedy} is accepted and treated as {@code default}.
     */
    public String planMode = "default";
    /** Target curve label for default plan (e.g. gradual-rise). */
    public String targetCurve = "gradual-rise";
    public String analyzerMode = "standard";
    public String pythonCmd = "python";
    public String analyzerScript = "python/audio_analyzer.py";
    public int analyzeWorkers = 4;
    public Integer count = 8;
    /** Planner style label (optional). */
    public String style;
    /** Partial title match for opening track; when set, that track stays in slot 1 after shaping. */
    public String firstTrackTitle;
    /**
     * {@code all} or one of {@code club}, {@code rave}, {@code house_party}, or comma-separated list.
     */
    public String shapeModes = "all";
    /** Default genre tag when MP3 has none (optional). */
    public String defaultGenre;
    /**
     * When {@code true}, ignore per-file analyze cache (same as CLI {@code --reanalyze}).
     * Optional; default is to reuse cache under {@code ~/.djset/analyze-cache/}.
     */
    public Boolean reanalyze;
}
