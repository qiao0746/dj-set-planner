package com.djset.service;

import com.djset.model.SetPlan;
import com.djset.model.Track;

import java.util.List;
import java.util.Locale;

public class BuildFromFirstService {
    private final SetPlannerService setPlannerService = new SetPlannerService();

    public SetPlan buildFromFirstTrack(
            List<Track> tracks,
            String firstTrackId,
            String style,
            String targetCurve,
            Integer count
    ) {
        return setPlannerService.createPlanFromFirstTrack(tracks, firstTrackId, style, targetCurve, count);
    }

    public SetPlan buildFromFirstTrackTitle(
            List<Track> tracks,
            String firstTrackTitle,
            String style,
            String targetCurve,
            Integer count
    ) {
        if (firstTrackTitle == null || firstTrackTitle.isBlank()) {
            throw new IllegalArgumentException("First track title is required.");
        }
        String query = firstTrackTitle.trim().toLowerCase(Locale.ROOT);
        Track match = tracks.stream()
                .filter(t -> t.getTitle() != null)
                .filter(t -> t.getTitle().toLowerCase(Locale.ROOT).contains(query))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No track title matched: " + firstTrackTitle));
        return setPlannerService.createPlanFromFirstTrack(tracks, match.getId(), style, targetCurve, count);
    }
}
