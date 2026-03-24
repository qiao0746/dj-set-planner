package com.djset.cli;

import com.djset.PlanLimits;
import com.djset.model.RecommendationResult;
import com.djset.model.Track;
import com.djset.service.RecommendService;
import com.djset.util.JsonUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.List;

@Command(name = "recommend", description = "Recommend next songs from current song.")
public class RecommendCommand implements Runnable {
    private final RecommendService recommendService = new RecommendService();

    @Option(names = "--input", required = true, description = "Input JSON file path.")
    private String input;

    @Option(names = "--current-track-id", required = true, description = "Current track id.")
    private String currentTrackId;

    @Option(
            names = "--count",
            description = "Number of recommendations (" + PlanLimits.MIN_SET_SIZE + "-" + PlanLimits.MAX_SET_SIZE + ").",
            defaultValue = "5"
    )
    private int count;

    @Override
    public void run() {
        List<Track> tracks = JsonUtil.readTracks(Path.of(input));
        RecommendationResult result = recommendService.recommendNextSongs(tracks, currentTrackId, count);
        System.out.println(JsonUtil.toJson(result));
    }
}
