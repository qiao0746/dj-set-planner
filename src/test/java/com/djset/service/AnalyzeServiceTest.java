package com.djset.service;

import com.djset.model.Track;
import com.djset.util.JsonUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyzeServiceTest {

    private final AnalyzeService analyzeService = new AnalyzeService();

    @Test
    void analyzeWritesJsonForMp3FilesEvenWhenMetadataParsingFails(@TempDir Path tempDir) throws IOException {
        Path inputDir = tempDir.resolve("music");
        Files.createDirectories(inputDir);

        // Not a real MP3 file; service should still emit a fallback track entry.
        Path fakeMp3 = inputDir.resolve("my-track.mp3");
        Files.writeString(fakeMp3, "not a real mp3");

        Path output = tempDir.resolve("tracks.json");
        int count = analyzeService.analyze(inputDir.toString(), output.toString(), "house");

        assertEquals(1, count);

        List<Track> tracks = JsonUtil.readTracks(output);
        assertEquals(1, tracks.size());
        Track t = tracks.get(0);
        assertEquals("my-track", t.getTitle());
        assertEquals("Unknown Artist", t.getArtist());
        assertEquals(List.of("house"), t.getGenreTags());
    }

    @Test
    void analyzeIgnoresNonMp3Files(@TempDir Path tempDir) throws IOException {
        Path inputDir = tempDir.resolve("music");
        Files.createDirectories(inputDir);
        Files.writeString(inputDir.resolve("notes.txt"), "hello");

        Path output = tempDir.resolve("tracks.json");
        int count = analyzeService.analyze(inputDir.toString(), output.toString(), null);

        assertEquals(0, count);
        assertTrue(Files.exists(output));
        assertEquals(0, JsonUtil.readTracks(output).size());
    }

    @Test
    void analyzeSupportsParallelWorkers(@TempDir Path tempDir) throws IOException {
        Path inputDir = tempDir.resolve("music");
        Files.createDirectories(inputDir);
        Files.writeString(inputDir.resolve("a.mp3"), "fake");
        Files.writeString(inputDir.resolve("b.mp3"), "fake");

        Path output = tempDir.resolve("tracks.json");
        int count = analyzeService.analyze(
                inputDir.toString(),
                output.toString(),
                "house",
                "python",
                "python/audio_analyzer.py",
                2
        );

        assertEquals(2, count);
        assertEquals(2, JsonUtil.readTracks(output).size());
    }

    @Test
    void analyzeRejectsInvalidWorkerCount(@TempDir Path tempDir) throws IOException {
        Path inputDir = tempDir.resolve("music");
        Files.createDirectories(inputDir);
        Path output = tempDir.resolve("tracks.json");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> analyzeService.analyze(
                        inputDir.toString(),
                        output.toString(),
                        null,
                        "python",
                        "python/audio_analyzer.py",
                        0
                )
        );
        assertTrue(ex.getMessage().contains("between 1 and 32"));
    }

    @Test
    void checkAnalyzerEnvironmentReportsNotReadyForMissingTools(@TempDir Path tempDir) throws IOException {
        Path fakeScript = tempDir.resolve("missing.py");
        AnalyzeService.AnalyzerEnvironmentStatus status =
                analyzeService.checkAnalyzerEnvironment("python-command-that-does-not-exist", fakeScript.toString());

        assertEquals(false, status.ready());
        assertEquals(false, status.pythonAvailable());
        assertEquals(false, status.analyzerScriptExists());
    }
}
