package com.djset.cli;

import com.djset.PlanLimits;
import com.djset.model.SetPlan;
import com.djset.model.SetShapePlan;
import com.djset.model.Track;
import com.djset.service.BuildFromFirstService;
import com.djset.service.SetShapeService;
import com.djset.util.JsonUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Command(name = "shape-from-first", description = "Compare set-shape modes from a chosen first track.")
public class ShapeFromFirstCommand implements Runnable {
    private static final List<String> MODES = List.of("club", "rave", "house_party");

    private final BuildFromFirstService buildFromFirstService = new BuildFromFirstService();
    private final SetShapeService setShapeService = new SetShapeService();

    @Option(names = "--input", required = true, description = "Input JSON file path.")
    private String input;

    @Option(names = "--first-track-title", required = true, description = "Track title (partial match) to start the seed set with.")
    private String firstTrackTitle;

    @Option(
            names = "--count",
            required = true,
            description = "Number of songs to include (" + PlanLimits.MIN_SET_SIZE + "-" + PlanLimits.MAX_SET_SIZE + ")."
    )
    private Integer count;

    @Option(names = "--style", description = "Optional style label for seed generation.")
    private String style;

    @Override
    public void run() {
        List<Track> tracks = JsonUtil.readTracks(Path.of(input));
        SetPlan seed = buildFromFirstService.buildFromFirstTrackTitle(
                tracks, firstTrackTitle, style, "seed", count
        );
        List<Track> seedTracks = seed.getOrderedTracks();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("seedFirstTrackTitleQuery", firstTrackTitle);
        out.put("seedTrackCount", seedTracks.size());
        out.put("seedTracks", seedTracks);

        Map<String, SetShapePlan> byMode = new LinkedHashMap<>();
        for (String mode : MODES) {
            // Seed opener (matched title) stays slot 0; remaining slots follow curve optimization.
            byMode.put(mode, setShapeService.shapeSet(seedTracks, mode, true));
        }
        out.put("shapeByMode", byMode);
        System.out.println(JsonUtil.toJson(out));
    }
}
