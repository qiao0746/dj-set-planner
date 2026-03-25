package com.djset.ui.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * JSON body for {@code POST /api/clear-cache}: remove the analyze-cache bucket for one music folder.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClearCacheRequest {
    /** Full path to the music library (same as planner / next-songs forms). */
    public String musicDir;
}
