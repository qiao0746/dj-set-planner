package com.djset.cli;

import com.djset.model.SetPlan;
import com.djset.model.Track;
import com.djset.service.BuildSetService;
import com.djset.util.JsonUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@Command(name = "build-set", description = "Build a set from selected songs.")
public class BuildSetCommand implements Runnable {
    private final BuildSetService buildSetService = new BuildSetService();

    @Option(names = "--input", required = true, description = "Input JSON file path.")
    private String input;

    @Option(names = "--selected-ids", required = true, description = "Comma separated selected track ids.")
    private String selectedIds;

    @Option(names = "--style", description = "Optional style label.")
    private String style;

    @Option(names = "--target-curve", description = "Optional target curve.")
    private String targetCurve;

    @Option(names = "--count", description = "Number of songs to include (1-10).")
    private Integer count;

    @Override
    public void run() {
        List<Track> tracks = JsonUtil.readTracks(Path.of(input));
        List<String> ids = Arrays.stream(selectedIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        SetPlan plan = buildSetService.buildFromSelectedSongs(tracks, ids, style, targetCurve, count);
        System.out.println(JsonUtil.toJson(plan));
    }
}
