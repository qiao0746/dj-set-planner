package com.djset.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

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
