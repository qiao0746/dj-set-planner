package com.djset.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Resolves stable JSON paths for interactive UI analyze results: one folder per music library
 * (hash of absolute music path) and one file per analyze options (mode, genre default, python, script).
 * <p>
 * Files live under {@code ${user.home}/.djset/analyze-cache/}. The Java {@link com.djset.service.AnalyzeService}
 * writes {@code {path}.cache.json} next to each output for per-track feature reuse.
 */
public final class UiAnalyzeCache {

    private static final String CACHE_SEGMENT = ".djset";
    private static final String ANALYZE_SEGMENT = "analyze-cache";

    private UiAnalyzeCache() {
    }

    /**
     * Absolute path for the main analyze JSON (tracks array). Parent directories are created.
     *
     * @param musicDir        absolute, normalized music folder
     * @param analyzerMode    e.g. standard, rekordbox-like
     * @param defaultGenre    may be empty
     * @param pythonCmd       resolved command string
     * @param analyzerScript  script path string
     */
    public static Path ensureAnalyzeOutputJson(
            Path musicDir,
            String analyzerMode,
            String defaultGenre,
            String pythonCmd,
            String analyzerScript
    ) throws java.io.IOException {
        Path base = Path.of(System.getProperty("user.home", "."))
                .resolve(CACHE_SEGMENT)
                .resolve(ANALYZE_SEGMENT)
                .resolve(folderKey(musicDir));
        Files.createDirectories(base);
        String fileName = optionsKey(
                analyzerMode,
                defaultGenre == null ? "" : defaultGenre,
                pythonCmd == null ? "" : pythonCmd,
                analyzerScript == null ? "" : analyzerScript
        ) + ".json";
        return base.resolve(fileName).toAbsolutePath().normalize();
    }

    static String folderKey(Path musicDirAbsoluteNormalized) {
        Objects.requireNonNull(musicDirAbsoluteNormalized, "musicDir");
        String s = musicDirAbsoluteNormalized.toAbsolutePath().normalize().toString();
        return sha256Hex(s);
    }

    private static Path analyzeCacheRoot() {
        return Path.of(System.getProperty("user.home", "."))
                .resolve(CACHE_SEGMENT)
                .resolve(ANALYZE_SEGMENT);
    }

    /**
     * Deletes the UI analyze cache bucket for this music library (all option variants and sidecar {@code *.cache.json} files).
     *
     * @param musicDir absolute or relative path to the music folder (must exist and be a directory)
     * @return summary for API responses
     */
    public static Map<String, Object> clearLibraryCache(Path musicDir) throws IOException {
        Objects.requireNonNull(musicDir, "musicDir");
        Path normalized = musicDir.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("Music folder does not exist: " + normalized);
        }
        Path bucket = analyzeCacheRoot().resolve(folderKey(normalized)).toAbsolutePath().normalize();
        if (!Files.exists(bucket)) {
            return Map.of(
                    "removedEntries", 0,
                    "hadCache", false,
                    "cacheFolder", bucket.toString()
            );
        }
        Path rootNorm = analyzeCacheRoot().toAbsolutePath().normalize();
        if (!bucket.startsWith(rootNorm)) {
            throw new IllegalArgumentException("Refusing to delete outside analyze cache root.");
        }
        List<Path> paths = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(bucket)) {
            walk.forEach(paths::add);
        }
        paths.sort(Comparator.reverseOrder());
        int removed = 0;
        for (Path p : paths) {
            if (Files.deleteIfExists(p)) {
                removed++;
            }
        }
        return Map.of(
                "removedEntries", removed,
                "hadCache", true,
                "cacheFolder", bucket.toString()
        );
    }

    /**
     * Short fingerprint for options that affect Python / tag defaults (separate file per combo).
     */
    static String optionsKey(String analyzerMode, String defaultGenre, String pythonCmd, String analyzerScript) {
        String mode = analyzerMode == null || analyzerMode.isBlank() ? "standard" : analyzerMode.trim().toLowerCase(Locale.ROOT);
        String genre = defaultGenre == null ? "" : defaultGenre.trim();
        String py = pythonCmd == null ? "" : pythonCmd.trim();
        String script = analyzerScript == null ? "" : analyzerScript.trim();
        String combined = mode + "\u001f" + genre + "\u001f" + py + "\u001f" + script;
        String hex = sha256Hex(combined);
        return hex.substring(0, Math.min(20, hex.length()));
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
