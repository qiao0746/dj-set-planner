package com.djset.service;

import com.djset.model.SetPlan;
import com.djset.model.Track;
import com.djset.util.JsonUtil;

import java.nio.file.Path;
import java.util.List;

public class PlanService {
    private final SetPlannerService setPlannerService = new SetPlannerService();

    public SetPlan plan(String inputPath, String style, String targetCurve) {
        return plan(inputPath, style, targetCurve, null);
    }

    public SetPlan plan(String inputPath, String style, String targetCurve, Integer requestedCount) {
        Path path = requireInputPath(inputPath);
        List<Track> tracks = loadTracks(path);
        return setPlannerService.createPlan(tracks, style, targetCurve, requestedCount);
    }

    private Path requireInputPath(String inputPath) {
        if (inputPath == null || inputPath.isBlank()) {
            throw new IllegalArgumentException("Input path is required.");
        }
        return Path.of(inputPath);
    }

    private List<Track> loadTracks(Path path) {
        List<Track> tracks = JsonUtil.readTracks(path);
        if (tracks.isEmpty()) {
            throw new IllegalArgumentException("No tracks found in input.");
        }
        return tracks;
    }
}
