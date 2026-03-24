package com.djset.cli;

import com.djset.model.SetPlan;
import com.djset.model.Track;
import com.djset.service.BuildFromFirstService;
import com.djset.util.JsonUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.List;

@Command(name = "build-from-first", description = "Build a DJ list from a chosen first song.")
public class BuildFromFirstCommand implements Runnable {
    private final BuildFromFirstService buildFromFirstService = new BuildFromFirstService();

    @Option(names = "--input", required = true, description = "Input JSON file path.")
    private String input;

    @Option(names = "--first-track-id", description = "Track id to start the set with.")
    private String firstTrackId;

    @Option(names = "--first-track-title", description = "Track title (partial match) to start the set with.")
    private String firstTrackTitle;

    @Option(names = "--count", required = true, description = "Number of songs to include (1-10).")
    private Integer count;

    @Option(names = "--style", description = "Optional style label.")
    private String style;

    @Option(names = "--target-curve", description = "Optional target curve.")
    private String targetCurve;

    @Override
    public void run() {
        List<Track> tracks = JsonUtil.readTracks(Path.of(input));
        SetPlan plan;
        if (firstTrackId != null && !firstTrackId.isBlank()) {
            plan = buildFromFirstService.buildFromFirstTrack(tracks, firstTrackId, style, targetCurve, count);
        } else if (firstTrackTitle != null && !firstTrackTitle.isBlank()) {
            plan = buildFromFirstService.buildFromFirstTrackTitle(tracks, firstTrackTitle, style, targetCurve, count);
        } else {
            throw new IllegalArgumentException("Either --first-track-id or --first-track-title is required.");
        }
        System.out.println(JsonUtil.toJson(plan));
    }
}
