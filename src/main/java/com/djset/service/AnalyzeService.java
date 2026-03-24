package com.djset.service;

import com.djset.model.Track;
import com.djset.util.JsonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AnalyzeService {
    private static final String UNKNOWN_ARTIST = "Unknown Artist";
    private static final String UNKNOWN_TITLE = "Unknown Title";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MIN_WORKERS = 1;
    private static final int MAX_WORKERS = 32;
    private static final String MODE_STANDARD = "standard";
    private static final String MODE_REKORDBOX_LIKE = "rekordbox-like";
    private static final double FEATURE_SELECTION_CORR_THRESHOLD = 0.95;

    public int analyze(String inputDir, String outputPath, String defaultGenreTag) {
        return analyze(inputDir, outputPath, defaultGenreTag, "python", "python/audio_analyzer.py", 1, MODE_STANDARD, false);
    }

    public int analyze(
            String inputDir,
            String outputPath,
            String defaultGenreTag,
            String pythonCmd,
            String analyzerScriptPath
    ) {
        return analyze(inputDir, outputPath, defaultGenreTag, pythonCmd, analyzerScriptPath, 1, MODE_STANDARD, false);
    }

    public int analyze(
            String inputDir,
            String outputPath,
            String defaultGenreTag,
            String pythonCmd,
            String analyzerScriptPath,
            int analyzeWorkers
    ) {
        return analyze(inputDir, outputPath, defaultGenreTag, pythonCmd, analyzerScriptPath, analyzeWorkers, MODE_STANDARD, false);
    }

    public int analyze(
            String inputDir,
            String outputPath,
            String defaultGenreTag,
            String pythonCmd,
            String analyzerScriptPath,
            int analyzeWorkers,
            String mode,
            boolean reanalyze
    ) {
        Path sourceDir = requireInputDir(inputDir);
        Path output = requireOutputPath(outputPath);
        int workers = requireWorkers(analyzeWorkers);
        String analyzerMode = requireMode(mode);
        AnalyzerEnvironmentStatus status = checkAnalyzerEnvironment(pythonCmd, analyzerScriptPath);
        String pythonExecutable = status.resolvedPythonCommand();
        Path cachePath = Path.of(output.toString() + ".cache.json");
        Map<String, AnalysisFeatures> cache = reanalyze ? new HashMap<>() : loadCache(cachePath);
        List<Path> mp3Files = findMp3Files(sourceDir);
        List<Track> tracks = analyzeFiles(
                mp3Files,
                sourceDir,
                defaultGenreTag,
                pythonExecutable,
                analyzerScriptPath,
                workers,
                analyzerMode,
                cache
        );
        JsonUtil.writeTracks(output, tracks);
        writeConsolidationSummary(output, tracks);
        writeFeatureSelectedTracks(output, tracks);
        saveCache(cachePath, cache);
        return tracks.size();
    }

    public AnalyzerEnvironmentStatus checkAnalyzerEnvironment(String pythonCmd, String analyzerScriptPath) {
        String resolvedPython = resolvePythonCommand(pythonCmd);
        boolean scriptExists = analyzerScriptPath != null && !analyzerScriptPath.isBlank()
                && Files.exists(Path.of(analyzerScriptPath));
        boolean pythonAvailable = commandSucceeds(List.of(resolvedPython, "--version"));
        boolean depsReady = pythonAvailable && commandSucceeds(
                List.of(resolvedPython, "-c", "import librosa, numpy; print('ok')")
        );
        boolean ready = scriptExists && pythonAvailable && depsReady;
        return new AnalyzerEnvironmentStatus(
                resolvedPython,
                scriptExists,
                pythonAvailable,
                depsReady,
                ready
        );
    }
    private int requireWorkers(int workers) {
        if (workers < MIN_WORKERS || workers > MAX_WORKERS) {
            throw new IllegalArgumentException("Analyze workers must be between 1 and 32.");
        }
        return workers;
    }

    private String requireMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return MODE_STANDARD;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        if (!MODE_STANDARD.equals(normalized) && !MODE_REKORDBOX_LIKE.equals(normalized)) {
            throw new IllegalArgumentException("Mode must be 'standard' or 'rekordbox-like'.");
        }
        return normalized;
    }

    private String resolvePythonCommand(String pythonCmd) {
        String cmd = (pythonCmd == null || pythonCmd.isBlank()) ? "python" : pythonCmd.trim();
        if (!"python".equalsIgnoreCase(cmd)) {
            return cmd;
        }

        Optional<Path> fromPath = findExecutableOnPath("python.exe");
        if (fromPath.isPresent()) {
            return fromPath.get().toString();
        }

        Optional<Path> fromCommon = findPythonInCommonInstallLocations();
        if (fromCommon.isPresent()) {
            return fromCommon.get().toString();
        }
        return cmd;
    }

    private Optional<Path> findExecutableOnPath(String executableName) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return Optional.empty();
        }
        for (String dir : pathEnv.split(";")) {
            if (dir == null || dir.isBlank()) continue;
            Path candidate = Paths.get(dir.trim(), executableName);
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private Optional<Path> findPythonInCommonInstallLocations() {
        List<Path> roots = new ArrayList<>();
        addIfPresent(roots, System.getenv("LOCALAPPDATA"));
        addIfPresent(roots, System.getenv("ProgramFiles"));
        addIfPresent(roots, System.getenv("ProgramFiles(x86)"));

        for (Path root : roots) {
            Path programsPython = root.resolve("Programs").resolve("Python");
            Optional<Path> first = findPythonExeInDirectory(programsPython);
            if (first.isPresent()) return first;

            Optional<Path> second = findPythonExeInDirectory(root.resolve("Python"));
            if (second.isPresent()) return second;
        }
        return Optional.empty();
    }

    private void addIfPresent(List<Path> roots, String envValue) {
        if (envValue == null || envValue.isBlank()) return;
        Path path = Paths.get(envValue);
        if (Files.exists(path) && Files.isDirectory(path)) {
            roots.add(path);
        }
    }

    private Optional<Path> findPythonExeInDirectory(Path baseDir) {
        if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
            return Optional.empty();
        }
        try (var stream = Files.list(baseDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(dir -> dir.resolve("python.exe"))
                    .filter(path -> Files.exists(path) && Files.isRegularFile(path))
                    .sorted()
                    .findFirst();
        } catch (IOException ex) {
            return Optional.empty();
        }
    }


    private Path requireInputDir(String inputDir) {
        if (inputDir == null || inputDir.isBlank()) {
            throw new IllegalArgumentException("Input directory is required.");
        }
        Path path = Path.of(inputDir);
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new IllegalArgumentException("Input directory does not exist: " + inputDir);
        }
        return path;
    }

    private Path requireOutputPath(String outputPath) {
        if (outputPath == null || outputPath.isBlank()) {
            throw new IllegalArgumentException("Output path is required.");
        }
        return Path.of(outputPath);
    }

    private List<Path> findMp3Files(Path sourceDir) {
        try (var stream = Files.walk(sourceDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isMp3)
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to scan input directory: " + sourceDir, ex);
        }
    }

    private boolean isMp3(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".mp3");
    }

    private List<Track> analyzeFiles(
            List<Path> files,
            Path sourceDir,
            String defaultGenreTag,
            String pythonCmd,
            String analyzerScriptPath,
            int workers,
            String mode,
            Map<String, AnalysisFeatures> cache
    ) {
        if (workers == 1 || files.size() <= 1) {
            return analyzeFilesSequential(files, sourceDir, defaultGenreTag, pythonCmd, analyzerScriptPath, mode, cache);
        }
        return analyzeFilesParallel(files, sourceDir, defaultGenreTag, pythonCmd, analyzerScriptPath, workers, mode, cache);
    }

    private List<Track> analyzeFilesSequential(
            List<Path> files,
            Path sourceDir,
            String defaultGenreTag,
            String pythonCmd,
            String analyzerScriptPath,
            String mode,
            Map<String, AnalysisFeatures> cache
    ) {
        List<Track> tracks = new ArrayList<>();
        for (Path file : files) {
            tracks.add(analyzeSingleFile(file, sourceDir, defaultGenreTag, pythonCmd, analyzerScriptPath, mode, cache));
        }
        return tracks;
    }

    private List<Track> analyzeFilesParallel(
            List<Path> files,
            Path sourceDir,
            String defaultGenreTag,
            String pythonCmd,
            String analyzerScriptPath,
            int workers,
            String mode,
            Map<String, AnalysisFeatures> cache
    ) {
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Callable<Track>> tasks = new ArrayList<>();
            for (Path file : files) {
                tasks.add(() -> analyzeSingleFile(file, sourceDir, defaultGenreTag, pythonCmd, analyzerScriptPath, mode, cache));
            }
            List<Future<Track>> futures = executor.invokeAll(tasks);
            List<Track> tracks = new ArrayList<>();
            for (Future<Track> future : futures) {
                tracks.add(future.get());
            }
            return tracks;
        } catch (Exception ex) {
            throw new IllegalStateException("Parallel analysis failed.", ex);
        } finally {
            executor.shutdown();
        }
    }

    private Track analyzeSingleFile(
            Path file,
            Path sourceDir,
            String defaultGenreTag,
            String pythonCmd,
            String analyzerScriptPath,
            String mode,
            Map<String, AnalysisFeatures> cache
    ) {
        AnalysisFeatures features = resolveFeatures(
                file, sourceDir, pythonCmd, analyzerScriptPath, requireMode(mode), cache);
        String fallbackTitle = stripExtension(file.getFileName().toString());
        String title = fallbackTitle;
        String artist = UNKNOWN_ARTIST;
        double durationSec = 0.0;
        List<String> genreTags = defaultGenreTag == null || defaultGenreTag.isBlank()
                ? List.of()
                : List.of(defaultGenreTag.trim());

        try {
            AudioFile audioFile = AudioFileIO.read(file.toFile());
            Tag tag = audioFile.getTag();
            AudioHeader header = audioFile.getAudioHeader();

            title = firstNonBlank(extractTag(tag, FieldKey.TITLE), fallbackTitle, UNKNOWN_TITLE);
            artist = firstNonBlank(extractTag(tag, FieldKey.ARTIST), UNKNOWN_ARTIST);
            durationSec = header == null ? 0.0 : header.getTrackLength();
            genreTags = parseTags(extractTag(tag, FieldKey.GENRE));
            if ((genreTags.isEmpty()) && defaultGenreTag != null && !defaultGenreTag.isBlank()) {
                genreTags = List.of(defaultGenreTag.trim());
            }
        } catch (Exception ex) {
            // Keep defaults; audio features may still be present from Python analysis.
        }

        return new Track(
                buildTrackId(file, sourceDir),
                title,
                artist,
                features.bpm,
                features.tempoConfidence,
                features.key,
                features.keyConfidence,
                features.energy,
                features.danceability,
                features.djEnergy,
                features.intensity,
                features.tension,
                buildAnalysisDebug(features),
                genreTags,
                List.of(),
                durationSec
        );
    }

    private AnalysisFeatures resolveFeatures(
            Path file,
            Path sourceDir,
            String pythonCmd,
            String analyzerScriptPath,
            String normalizedMode,
            Map<String, AnalysisFeatures> cache
    ) {
        String cacheKey = buildCacheKey(file, sourceDir, normalizedMode);
        synchronized (cache) {
            AnalysisFeatures cached = cache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        AnalysisFeatures fresh = runPythonAnalyzer(file, pythonCmd, analyzerScriptPath, normalizedMode);
        synchronized (cache) {
            cache.put(cacheKey, fresh);
        }
        return fresh;
    }

    private AnalysisFeatures runPythonAnalyzer(Path file, String pythonCmd, String analyzerScriptPath, String mode) {
        if (pythonCmd == null || pythonCmd.isBlank() || analyzerScriptPath == null || analyzerScriptPath.isBlank()) {
            return AnalysisFeatures.empty();
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    pythonCmd,
                    analyzerScriptPath,
                    "--input",
                    file.toString(),
                    "--mode",
                    mode
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0 || output.isBlank()) {
                return AnalysisFeatures.empty();
            }
            return parseAnalysisFeatures(output);
        } catch (Exception ex) {
            return AnalysisFeatures.empty();
        }
    }

    /**
     * Stable id for a file (path relative to music dir, size, mtime). Does not include analyzer mode.
     */
    private String buildFileFingerprint(Path file, Path sourceDir) {
        try {
            String relative = sourceDir.relativize(file).toString().replace('\\', '/');
            long size = Files.size(file);
            long modified = Files.getLastModifiedTime(file).toMillis();
            return relative + "|" + size + "|" + modified;
        } catch (IOException ex) {
            return sourceDir.relativize(file).toString().replace('\\', '/');
        }
    }

    /**
     * Cache map key: file fingerprint + analyzer mode so standard vs rekordbox-like never share entries.
     */
    private String buildCacheKey(Path file, Path sourceDir, String normalizedMode) {
        return buildFileFingerprint(file, sourceDir) + "|" + normalizedMode;
    }

    /**
     * Legacy cache keys were {@code rel|size|mtime} with no mode suffix; they must not be reused after
     * switching standard vs rekordbox-like. Keys written since this change end with {@code |standard}
     * or {@code |rekordbox-like}.
     */
    private static boolean isModeScopedCacheKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        return key.endsWith("|" + MODE_STANDARD) || key.endsWith("|" + MODE_REKORDBOX_LIKE);
    }

    @SuppressWarnings("unchecked")
    private Map<String, AnalysisFeatures> loadCache(Path cachePath) {
        if (!Files.exists(cachePath)) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> raw = MAPPER.readValue(cachePath.toFile(), Map.class);
            Map<String, AnalysisFeatures> out = new HashMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                if (!isModeScopedCacheKey(e.getKey())) {
                    continue;
                }
                if (e.getValue() instanceof Map<?, ?> row) {
                    out.put(e.getKey(), new AnalysisFeatures(
                            asDouble(row.get("bpm")),
                            asDouble(row.get("tempoConfidence")),
                            asString(row.get("key")),
                            asDouble(row.get("keyConfidence")),
                            asDouble(row.get("loudness")),
                            asDouble(row.get("spectralCentroid")),
                            asDouble(row.get("spectralFlux")),
                            asDouble(row.get("energy")),
                            asDouble(row.get("danceability")),
                            asDouble(row.get("djEnergy")),
                            asDouble(row.get("intensity")),
                            asDouble(row.get("tension")),
                            asDouble(row.get("dropScore")),
                            asDouble(row.get("bpmScore")),
                            asDouble(row.get("loudnessScore")),
                            asDouble(row.get("centroidScore")),
                            asDouble(row.get("fluxScore")),
                            asDouble(row.get("loudnessVarianceScore")),
                            asDouble(row.get("dynamicRangeScore")),
                            asDouble(row.get("buildupScore"))
                    ));
                }
            }
            return out;
        } catch (Exception ex) {
            return new HashMap<>();
        }
    }

    private void saveCache(Path cachePath, Map<String, AnalysisFeatures> cache) {
        try {
            Map<String, Map<String, Object>> raw = new HashMap<>();
            for (Map.Entry<String, AnalysisFeatures> e : cache.entrySet()) {
                AnalysisFeatures f = e.getValue();
                Map<String, Object> row = new HashMap<>();
                row.put("bpm", f.bpm);
                row.put("tempoConfidence", f.tempoConfidence);
                row.put("key", f.key);
                row.put("keyConfidence", f.keyConfidence);
                row.put("loudness", f.loudness);
                row.put("spectralCentroid", f.spectralCentroid);
                row.put("spectralFlux", f.spectralFlux);
                row.put("energy", f.energy);
                row.put("danceability", f.danceability);
                row.put("djEnergy", f.djEnergy);
                row.put("intensity", f.intensity);
                row.put("tension", f.tension);
                row.put("dropScore", f.dropScore);
                row.put("bpmScore", f.bpmScore);
                row.put("loudnessScore", f.loudnessScore);
                row.put("centroidScore", f.centroidScore);
                row.put("fluxScore", f.fluxScore);
                row.put("loudnessVarianceScore", f.loudnessVarianceScore);
                row.put("dynamicRangeScore", f.dynamicRangeScore);
                row.put("buildupScore", f.buildupScore);
                raw.put(e.getKey(), row);
            }
            MAPPER.writeValue(cachePath.toFile(), raw);
        } catch (Exception ignored) {
            // Cache write should not fail analysis.
        }
    }

    private boolean commandSucceeds(List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private void writeConsolidationSummary(Path output, List<Track> tracks) {
        Path summaryPath = Path.of(output.toString() + ".summary.json");
        try {
            Map<String, Object> payload = buildConsolidationSummary(tracks);
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(summaryPath.toFile(), payload);
        } catch (Exception ignored) {
            // Summary write should not fail analysis.
        }
    }

    private Map<String, Object> buildConsolidationSummary(List<Track> tracks) {
        List<String> componentNames = List.of(
                "bpmScore",
                "loudnessScore",
                "centroidScore",
                "fluxScore",
                "loudnessVarianceScore",
                "dynamicRangeScore",
                "buildupScore",
                "dropScore"
        );
        List<double[]> columns = List.of(
                collectDebug(tracks, "bpmScore"),
                collectDebug(tracks, "loudnessScore"),
                collectDebug(tracks, "centroidScore"),
                collectDebug(tracks, "fluxScore"),
                collectDebug(tracks, "loudnessVarianceScore"),
                collectDebug(tracks, "dynamicRangeScore"),
                collectDebug(tracks, "buildupScore"),
                collectDebug(tracks, "dropScore")
        );

        Map<String, Map<String, Double>> similarityMatrix = new LinkedHashMap<>();
        List<Map<String, Object>> highSimilarityPairs = new ArrayList<>();
        for (int i = 0; i < componentNames.size(); i++) {
            String rowName = componentNames.get(i);
            Map<String, Double> row = new LinkedHashMap<>();
            for (int j = 0; j < componentNames.size(); j++) {
                double corr = pearson(columns.get(i), columns.get(j));
                row.put(componentNames.get(j), round3(corr));
                if (j > i && Math.abs(corr) >= 0.80) {
                    Map<String, Object> pair = new LinkedHashMap<>();
                    pair.put("a", rowName);
                    pair.put("b", componentNames.get(j));
                    pair.put("correlation", round3(corr));
                    highSimilarityPairs.add(pair);
                }
            }
            similarityMatrix.put(rowName, row);
        }
        List<String> selectedComponents = selectComponentsByCorrelation(
                componentNames, columns, FEATURE_SELECTION_CORR_THRESHOLD
        );

        List<Map<String, Object>> perTrack = new ArrayList<>();
        for (Track t : tracks) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", t.getId());
            row.put("title", t.getTitle());
            row.put("djEnergy", round3(t.getDjEnergy()));
            row.put("intensity", round3(t.getIntensity()));
            row.put("tension", round3(t.getTension()));
            row.put("energyLabel", toLabel10(t.getDjEnergy()));
            row.put("intensityLabel", toLabel10(t.getIntensity()));
            row.put("tensionLabel", toLabel10(t.getTension()));
            row.put("humanProfile", buildProfileLabel(t));
            perTrack.add(row);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("note", "Consolidated DJ attributes with component similarity matrix.");
        payload.put("dimensions", List.of(
                "djEnergy(0-10): driving feel from BPM + loudness",
                "intensity(0-10): aggressiveness from brightness + spectral variation",
                "tension(0-10): buildup/release potential from dynamics + buildup"
        ));
        payload.put("componentSimilarityMatrix", similarityMatrix);
        payload.put("highSimilarityPairsAbsGte0.80", highSimilarityPairs);
        payload.put("selectedComponentsCorrThreshold", FEATURE_SELECTION_CORR_THRESHOLD);
        payload.put("selectedComponents", selectedComponents);
        payload.put("perTrackReadable", perTrack);
        return payload;
    }

    private void writeFeatureSelectedTracks(Path output, List<Track> tracks) {
        Path selectedPath = Path.of(output.toString() + ".selected.json");
        try {
            List<String> componentNames = List.of(
                    "bpmScore",
                    "loudnessScore",
                    "centroidScore",
                    "fluxScore",
                    "loudnessVarianceScore",
                    "dynamicRangeScore",
                    "buildupScore",
                    "dropScore"
            );
            List<double[]> columns = List.of(
                    collectDebug(tracks, "bpmScore"),
                    collectDebug(tracks, "loudnessScore"),
                    collectDebug(tracks, "centroidScore"),
                    collectDebug(tracks, "fluxScore"),
                    collectDebug(tracks, "loudnessVarianceScore"),
                    collectDebug(tracks, "dynamicRangeScore"),
                    collectDebug(tracks, "buildupScore"),
                    collectDebug(tracks, "dropScore")
            );
            List<String> selectedComponents = selectComponentsByCorrelation(
                    componentNames, columns, FEATURE_SELECTION_CORR_THRESHOLD
            );

            List<Map<String, Object>> compactRows = new ArrayList<>();
            for (Track t : tracks) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", t.getId());
                row.put("title", t.getTitle());
                row.put("artist", t.getArtist());
                row.put("bpm", round3(t.getBpm()));
                row.put("key", t.getKey());
                row.put("tempoConfidence", round3(t.getTempoConfidence()));
                row.put("keyConfidence", round3(t.getKeyConfidence()));
                row.put("djEnergy", round3(t.getDjEnergy()));
                row.put("intensity", round3(t.getIntensity()));
                row.put("tension", round3(t.getTension()));
                row.put("energyLabel", toLabel10(t.getDjEnergy()));
                row.put("intensityLabel", toLabel10(t.getIntensity()));
                row.put("tensionLabel", toLabel10(t.getTension()));
                row.put("profile", buildProfileLabel(t));
                row.put("selectedComponents", buildSelectedComponentValues(t, selectedComponents));
                row.put("genreTags", t.getGenreTags());
                row.put("durationSec", round3(t.getDurationSec()));
                compactRows.add(row);
            }
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(selectedPath.toFile(), compactRows);
        } catch (Exception ignored) {
            // Selected output write should not fail analysis.
        }
    }

    private List<String> selectComponentsByCorrelation(
            List<String> componentNames,
            List<double[]> columns,
            double threshold
    ) {
        List<String> selected = new ArrayList<>();
        List<double[]> selectedColumns = new ArrayList<>();
        for (int i = 0; i < componentNames.size(); i++) {
            double[] candidate = columns.get(i);
            boolean redundant = false;
            for (double[] kept : selectedColumns) {
                if (Math.abs(pearson(candidate, kept)) >= threshold) {
                    redundant = true;
                    break;
                }
            }
            if (!redundant) {
                selected.add(componentNames.get(i));
                selectedColumns.add(candidate);
            }
        }
        return selected;
    }

    private Map<String, Double> buildSelectedComponentValues(Track t, List<String> selectedComponents) {
        Map<String, Double> row = new LinkedHashMap<>();
        for (String name : selectedComponents) {
            double value = debugValue(t, name);
            if (value != 0.0 || t.getAnalysisDebug().containsKey(name)) {
                row.put(name, round3(value));
            }
        }
        return row;
    }

    private double debugValue(Track t, String key) {
        Double value = t.getAnalysisDebug().get(key);
        return value == null ? 0.0 : value;
    }

    private Map<String, Double> buildAnalysisDebug(AnalysisFeatures f) {
        Map<String, Double> row = new LinkedHashMap<>();
        row.put("loudness", f.loudness);
        row.put("spectralCentroid", f.spectralCentroid);
        row.put("spectralFlux", f.spectralFlux);
        row.put("bpmScore", f.bpmScore);
        row.put("loudnessScore", f.loudnessScore);
        row.put("centroidScore", f.centroidScore);
        row.put("fluxScore", f.fluxScore);
        row.put("loudnessVarianceScore", f.loudnessVarianceScore);
        row.put("dynamicRangeScore", f.dynamicRangeScore);
        row.put("buildupScore", f.buildupScore);
        row.put("dropScore", f.dropScore);
        return row;
    }

    private interface DoubleExtractor {
        double get(Track track);
    }

    private double[] collectDebug(List<Track> tracks, String key) {
        double[] values = new double[tracks.size()];
        for (int i = 0; i < tracks.size(); i++) {
            values[i] = debugValue(tracks.get(i), key);
        }
        return values;
    }

    private double[] collect(List<Track> tracks, DoubleExtractor extractor) {
        double[] values = new double[tracks.size()];
        for (int i = 0; i < tracks.size(); i++) {
            values[i] = extractor.get(tracks.get(i));
        }
        return values;
    }

    private double pearson(double[] x, double[] y) {
        int n = Math.min(x.length, y.length);
        if (n < 2) return 0.0;
        double meanX = 0.0;
        double meanY = 0.0;
        for (int i = 0; i < n; i++) {
            meanX += x[i];
            meanY += y[i];
        }
        meanX /= n;
        meanY /= n;
        double num = 0.0;
        double denX = 0.0;
        double denY = 0.0;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;
            num += dx * dy;
            denX += dx * dx;
            denY += dy * dy;
        }
        double den = Math.sqrt(denX * denY);
        if (den <= 1e-12) return 0.0;
        return num / den;
    }

    private String toLabel10(double value) {
        if (value < 3.5) return "low";
        if (value < 7.0) return "medium";
        return "high";
    }

    private String buildProfileLabel(Track t) {
        String e = toLabel10(t.getDjEnergy());
        String i = toLabel10(t.getIntensity());
        String ten = toLabel10(t.getTension());
        return "energy=" + e + ", intensity=" + i + ", tension=" + ten;
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private AnalysisFeatures parseAnalysisFeatures(String json) {
        try {
            Map<String, Object> row = MAPPER.readValue(json, Map.class);
            return new AnalysisFeatures(
                    asDouble(row.get("bpm")),
                    asDouble(row.get("tempoConfidence")),
                    asString(row.get("key")),
                    asDouble(row.get("keyConfidence")),
                    asDouble(row.get("loudness")),
                    asDouble(row.get("spectralCentroid")),
                    asDouble(row.get("spectralFlux")),
                    asDouble(row.get("energy")),
                    asDouble(row.get("danceability")),
                    asDouble(row.get("djEnergy")),
                    asDouble(row.get("intensity")),
                    asDouble(row.get("tension")),
                    asDouble(row.get("dropScore")),
                    asDouble(row.get("bpmScore")),
                    asDouble(row.get("loudnessScore")),
                    asDouble(row.get("centroidScore")),
                    asDouble(row.get("fluxScore")),
                    asDouble(row.get("loudnessVarianceScore")),
                    asDouble(row.get("dynamicRangeScore")),
                    asDouble(row.get("buildupScore"))
            );
        } catch (Exception ex) {
            return AnalysisFeatures.empty();
        }
    }

    private double asDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private String asString(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private String extractTag(Tag tag, FieldKey key) {
        if (tag == null) return null;
        try {
            return tag.getFirst(key);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String[] parts = raw.split("[,;/|]");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            String value = part.trim();
            if (!value.isEmpty()) {
                out.add(value);
            }
        }
        return out;
    }

    private String buildTrackId(Path file, Path sourceDir) {
        String relative = sourceDir.relativize(file).toString().replace('\\', '/');
        return UUID.nameUUIDFromBytes(relative.getBytes()).toString();
    }

    private String stripExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx <= 0 ? fileName : fileName.substring(0, idx);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static final class AnalysisFeatures {
        private final double bpm;
        private final double tempoConfidence;
        private final String key;
        private final double keyConfidence;
        private final double loudness;
        private final double spectralCentroid;
        private final double spectralFlux;
        private final double energy;
        private final double danceability;
        private final double djEnergy;
        private final double intensity;
        private final double tension;
        private final double dropScore;
        private final double bpmScore;
        private final double loudnessScore;
        private final double centroidScore;
        private final double fluxScore;
        private final double loudnessVarianceScore;
        private final double dynamicRangeScore;
        private final double buildupScore;

        private AnalysisFeatures(
                double bpm,
                double tempoConfidence,
                String key,
                double keyConfidence,
                double loudness,
                double spectralCentroid,
                double spectralFlux,
                double energy,
                double danceability,
                double djEnergy,
                double intensity,
                double tension,
                double dropScore,
                double bpmScore,
                double loudnessScore,
                double centroidScore,
                double fluxScore,
                double loudnessVarianceScore,
                double dynamicRangeScore,
                double buildupScore
        ) {
            this.bpm = bpm;
            this.tempoConfidence = tempoConfidence;
            this.key = key;
            this.keyConfidence = keyConfidence;
            this.loudness = loudness;
            this.spectralCentroid = spectralCentroid;
            this.spectralFlux = spectralFlux;
            this.energy = energy;
            this.danceability = danceability;
            this.djEnergy = djEnergy;
            this.intensity = intensity;
            this.tension = tension;
            this.dropScore = dropScore;
            this.bpmScore = bpmScore;
            this.loudnessScore = loudnessScore;
            this.centroidScore = centroidScore;
            this.fluxScore = fluxScore;
            this.loudnessVarianceScore = loudnessVarianceScore;
            this.dynamicRangeScore = dynamicRangeScore;
            this.buildupScore = buildupScore;
        }

        private static AnalysisFeatures empty() {
            return new AnalysisFeatures(
                    0.0, 0.0, null, 0.0,
                    0.0, 0.0, 0.0,
                    0.0, 0.0,
                    0.0, 0.0, 0.0,
                    0.0,
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
            );
        }
    }

    public record AnalyzerEnvironmentStatus(
            String resolvedPythonCommand,
            boolean analyzerScriptExists,
            boolean pythonAvailable,
            boolean dependenciesReady,
            boolean ready
    ) {
    }
}
