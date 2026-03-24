package com.djset.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiAnalyzeCacheTest {

    @Test
    void folderKeyStableForSamePath(@TempDir Path tmp) throws Exception {
        Path music = tmp.resolve("lib").toAbsolutePath().normalize();
        Files.createDirectories(music);
        String a = UiAnalyzeCache.folderKey(music);
        String b = UiAnalyzeCache.folderKey(music);
        assertEquals(a, b);
        assertEquals(64, a.length());
    }

    @Test
    void optionsKeyDiffersWhenModeOrGenreChanges() {
        String o1 = UiAnalyzeCache.optionsKey("standard", "house", "python", "python/audio_analyzer.py");
        String o2 = UiAnalyzeCache.optionsKey("rekordbox-like", "house", "python", "python/audio_analyzer.py");
        String o3 = UiAnalyzeCache.optionsKey("standard", "techno", "python", "python/audio_analyzer.py");
        assertNotEquals(o1, o2);
        assertNotEquals(o1, o3);
    }

    @Test
    void optionsKeyIsHexPrefix() {
        String o = UiAnalyzeCache.optionsKey("standard", "", "python", "python/audio_analyzer.py");
        assertEquals(20, o.length());
        assertTrue(o.chars().allMatch(c -> (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')));
    }
}
