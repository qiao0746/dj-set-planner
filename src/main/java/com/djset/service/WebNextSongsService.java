package com.djset.service;

import com.djset.model.NextSongIntent;
import com.djset.model.NextSongRecommendationItem;
import com.djset.model.Track;
import com.djset.ui.UiAnalyzeCache;
import com.djset.ui.dto.NextSongsRequest;
import com.djset.util.JsonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Analyze a music folder (with UI cache), resolve current + candidate tracks, then call {@link NextSongRecommendationService}.
 */
public class WebNextSongsService {

    private final AnalyzeService analyzeService = new AnalyzeService();
    private final NextSongRecommendationService nextSongRecommendationService = new NextSongRecommendationService();

    public Map<String, Object> run(NextSongsRequest req) throws IOException {
        if (req.musicDir == null || req.musicDir.isBlank()) {
            throw new IllegalArgumentException("musicDir is required.");
        }
        if (req.currentTrackTitle == null || req.currentTrackTitle.isBlank()) {
            throw new IllegalArgumentException("currentTrackTitle is required.");
        }

        Path musicPath = Path.of(req.musicDir.trim()).toAbsolutePath().normalize();
        if (!Files.isDirectory(musicPath)) {
            throw new IllegalArgumentException("Music folder does not exist: " + musicPath);
        }

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
        Track current = matchTitle(allTracks, req.currentTrackTitle.trim());
        List<Track> candidates = resolveCandidates(allTracks, current, req.candidateTrackTitles);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No candidate tracks after resolving titles.");
        }

        NextSongIntent intent = NextSongIntent.parse(req.intent);
        int n = req.numberOfRecommendations == null
                ? NextSongRecommendationService.DEFAULT_RECOMMENDATIONS
                : req.numberOfRecommendations;
        n = Math.max(NextSongRecommendationService.MIN_RECOMMENDATIONS,
                Math.min(NextSongRecommendationService.MAX_RECOMMENDATIONS, n));

        List<NextSongRecommendationItem> recs = nextSongRecommendationService.recommend(current, candidates, intent, n);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("musicDir", musicPath.toString());
        out.put("analyzeOutputPath", analyzeOut.toString());
        out.put("analyzedTrackCount", analyzed);
        out.put("currentTrack", current);
        out.put("intent", intent.name().toLowerCase(Locale.ROOT));
        out.put("numberOfRecommendations", n);
        out.put("recommendations", recs);
        return out;
    }

    private static Track matchTitle(List<Track> tracks, String firstTrackTitle) {
        String query = firstTrackTitle.toLowerCase(Locale.ROOT);
        return tracks.stream()
                .filter(t -> t.getTitle() != null)
                .filter(t -> t.getTitle().toLowerCase(Locale.ROOT).contains(query))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No track title matched: " + firstTrackTitle));
    }

    private static List<Track> resolveCandidates(List<Track> all, Track current, List<String> candidateTitles) {
        String cid = current.getId();
        Map<String, Track> othersById = new LinkedHashMap<>();
        for (Track t : all) {
            if (t.getId() == null || cid != null && cid.equals(t.getId())) {
                continue;
            }
            othersById.putIfAbsent(t.getId(), t);
        }

        if (candidateTitles == null || candidateTitles.isEmpty()) {
            return new ArrayList<>(othersById.values());
        }

        Set<String> picked = new LinkedHashSet<>();
        for (String raw : candidateTitles) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String q = raw.trim().toLowerCase(Locale.ROOT);
            for (Track t : all) {
                if (t.getId() == null || cid != null && cid.equals(t.getId())) {
                    continue;
                }
                if (t.getTitle() != null && t.getTitle().toLowerCase(Locale.ROOT).contains(q)) {
                    picked.add(t.getId());
                }
            }
        }

        List<Track> out = new ArrayList<>();
        for (String id : picked) {
            Track t = othersById.get(id);
            if (t != null) {
                out.add(t);
            }
        }
        return out;
    }
}
