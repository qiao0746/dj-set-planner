package com.djset.service;

import com.djset.model.SetPlan;
import com.djset.model.Track;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BuildSetService {
    private final SetPlannerService setPlannerService = new SetPlannerService();

    public SetPlan buildFromSelectedSongs(
            List<Track> tracks,
            List<String> selectedTrackIds,
            String style,
            String targetCurve,
            Integer count
    ) {
        if (tracks == null || tracks.isEmpty()) {
            throw new IllegalArgumentException("Track list cannot be empty.");
        }
        if (selectedTrackIds == null || selectedTrackIds.isEmpty()) {
            throw new IllegalArgumentException("Selected track ids are required.");
        }

        Set<String> selected = new HashSet<>(selectedTrackIds);
        List<Track> chosen = new ArrayList<>();
        for (Track track : tracks) {
            if (selected.contains(track.getId())) {
                chosen.add(track);
            }
        }
        if (chosen.isEmpty()) {
            throw new IllegalArgumentException("None of the selected track ids exist in input.");
        }
        return setPlannerService.createPlan(chosen, style, targetCurve, count);
    }
}
