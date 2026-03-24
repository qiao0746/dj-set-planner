package com.djset.service;

import com.djset.model.SetPlan;
import com.djset.model.SetShapePlan;
import com.djset.model.Track;
import com.djset.ui.UiAnalyzeCache;
import com.djset.ui.dto.PlanRunRequest;
import com.djset.util.JsonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Analyze a music folder then build a default (build-from-first) {@link SetPlan} or shaped {@link SetShapePlan}s for the web UI.
 */
public class WebPlanService {

    /** Default plan: build-from-first with transition scoring. API value {@code "default"}. */
    private static final String MODE_DEFAULT = "default";
    private static final String MODE_SHAPED = "shaped";
    private static final List<String> ALL_SHAPE_MODES = List.of("club", "rave", "house_party");

    private final AnalyzeService analyzeService = new AnalyzeService();
    private final BuildFromFirstService buildFromFirstService = new BuildFromFirstService();
    private final SetShapeService setShapeService = new SetShapeService();

    public Map<String, Object> run(PlanRunRequest req) throws IOException {
        if (req.musicDir == null || req.musicDir.isBlank()) {
            throw new IllegalArgumentException("musicDir is required.");
        }
        Path musicPath = Path.of(req.musicDir.trim()).toAbsolutePath().normalize();
        if (!Files.isDirectory(musicPath)) {
            throw new IllegalArgumentException("Music folder does not exist: " + musicPath);
        }

        int count = req.count == null ? 8 : req.count;
        count = Math.max(1, Math.min(10, count));

        int workers = Math.max(1, Math.min(32, req.analyzeWorkers));

        String analyzerMode = req.analyzerMode == null || req.analyzerMode.isBlank()
                ? "standard"
                : req.analyzerMode.trim();
        String script = req.analyzerScript == null || req.analyzerScript.isBlank()
                ? "python/audio_analyzer.py"
                : req.analyzerScript.trim();
        String python = req.pythonCmd == null || req.pythonCmd.isBlank()
                ? "python"
                : req.pythonCmd.trim();
        String genre = req.defaultGenre == null ? "" : req.defaultGenre.trim();
        boolean reanalyze = Boolean.TRUE.equals(req.reanalyze);

        Path analyzeOut = UiAnalyzeCache.ensureAnalyzeOutputJson(musicPath, analyzerMode, genre, python, script);

        int analyzed = analyzeService.analyze(
                musicPath.toString(),
                analyzeOut.toString(),
                genre,
                python,
                script,
                workers,
                analyzerMode,
                reanalyze
        );
        if (analyzed == 0) {
            throw new IllegalArgumentException("No MP3 files analyzed in: " + musicPath);
        }

        List<Track> allTracks = JsonUtil.readTracks(analyzeOut);
        String planMode = normalizePlanMode(req.planMode);

        if (MODE_DEFAULT.equals(planMode)) {
            return runDefaultPlan(req, musicPath, analyzeOut, analyzerMode, analyzed, allTracks, count);
        }
        return runShaped(req, musicPath, analyzeOut, analyzerMode, analyzed, allTracks, count);
    }

    private Map<String, Object> runDefaultPlan(
            PlanRunRequest req,
            Path musicPath,
            Path analyzeOut,
            String analyzerMode,
            int analyzed,
            List<Track> allTracks,
            int count
    ) {
        String curve = req.targetCurve == null || req.targetCurve.isBlank()
                ? "gradual-rise"
                : req.targetCurve.trim();
        String firstTitle = req.firstTrackTitle == null ? "" : req.firstTrackTitle.trim();

        SetPlan plan;
        String openerNote;
        if (firstTitle.isEmpty()) {
            Track first = allTracks.get(0);
            String openerId = first.getId();
            if (openerId == null || openerId.isBlank()) {
                throw new IllegalArgumentException(
                        "First analyzed track has no id; set opening track (partial title) in the form.");
            }
            plan = buildFromFirstService.buildFromFirstTrack(
                    allTracks, openerId, emptyToNull(req.style), curve, count
            );
            openerNote = "First track in analyze order: " + (first.getTitle() != null ? first.getTitle() : openerId);
        } else {
            plan = buildFromFirstService.buildFromFirstTrackTitle(
                    allTracks, firstTitle, emptyToNull(req.style), curve, count
            );
            openerNote = "Matched title: " + firstTitle;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("planMode", MODE_DEFAULT);
        out.put("musicDir", musicPath.toString());
        out.put("analyzedTrackCount", analyzed);
        out.put("analyzeOutputPath", analyzeOut.toString());
        out.put("analyzerMode", analyzerMode);
        out.put("setPlan", plan);
        out.put("openerNote", openerNote);
        out.put("seedTrackCount", plan.getOrderedTracks().size());
        return out;
    }

    private Map<String, Object> runShaped(
            PlanRunRequest req,
            Path musicPath,
            Path analyzeOut,
            String analyzerMode,
            int analyzed,
            List<Track> allTracks,
            int count
    ) {
        String firstTitle = req.firstTrackTitle == null ? "" : req.firstTrackTitle.trim();
        boolean pinFirst = !firstTitle.isEmpty();

        List<Track> seedTracks;
        if (pinFirst) {
            var seed = buildFromFirstService.buildFromFirstTrackTitle(
                    allTracks,
                    firstTitle,
                    emptyToNull(req.style),
                    "seed",
                    count
            );
            seedTracks = new ArrayList<>(seed.getOrderedTracks());
        } else {
            int n = Math.min(count, allTracks.size());
            seedTracks = new ArrayList<>(allTracks.subList(0, n));
        }

        List<String> shapeModes = parseShapeModes(req.shapeModes);
        Map<String, SetShapePlan> shapeByMode = new LinkedHashMap<>();
        for (String sm : shapeModes) {
            shapeByMode.put(sm, setShapeService.shapeSet(new ArrayList<>(seedTracks), sm, pinFirst));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("planMode", MODE_SHAPED);
        out.put("musicDir", musicPath.toString());
        out.put("analyzedTrackCount", analyzed);
        out.put("analyzeOutputPath", analyzeOut.toString());
        out.put("analyzerMode", analyzerMode);
        out.put("seedTrackCount", seedTracks.size());
        out.put("seedTracks", seedTracks);
        out.put("firstTrackTitleQuery", pinFirst ? firstTitle : null);
        out.put("pinFirst", pinFirst);
        out.put("shapeByMode", shapeByMode);
        return out;
    }

    private static String normalizePlanMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return MODE_DEFAULT;
        }
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if (MODE_DEFAULT.equals(t) || "greedy".equals(t) || "build-from-first".equals(t) || "transition".equals(t)) {
            return MODE_DEFAULT;
        }
        if (MODE_SHAPED.equals(t) || "shape".equals(t)) {
            return MODE_SHAPED;
        }
        throw new IllegalArgumentException("planMode must be default or shaped.");
    }

    private static String emptyToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private static List<String> parseShapeModes(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>(ALL_SHAPE_MODES);
        }
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if ("all".equals(t)) {
            return new ArrayList<>(ALL_SHAPE_MODES);
        }
        if (t.contains(",")) {
            List<String> out = new ArrayList<>();
            for (String part : t.split(",")) {
                String p = part.trim().toLowerCase(Locale.ROOT);
                if (!p.isEmpty()) {
                    validateShapeMode(p);
                    if (!out.contains(p)) {
                        out.add(p);
                    }
                }
            }
            if (out.isEmpty()) {
                return new ArrayList<>(ALL_SHAPE_MODES);
            }
            return out;
        }
        validateShapeMode(t);
        return List.of(t);
    }

    private static void validateShapeMode(String m) {
        if (!ALL_SHAPE_MODES.contains(m)) {
            throw new IllegalArgumentException("shapeModes must be all, club, rave, house_party, or a comma list thereof.");
        }
    }
}
