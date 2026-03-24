package com.djset.service;

import com.djset.model.SetPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanServiceTest {

    private final PlanService planService = new PlanService();

    @Test
    void planLoadsJsonAndAppliesCount(@TempDir Path tempDir) throws IOException {
        Path jsonFile = tempDir.resolve("tracks.json");
        Files.writeString(jsonFile, """
                [
                  {
                    "id":"t1","title":"A","artist":"DJ","bpm":122,"key":"8A","energy":0.30,
                    "danceability":0.6,"genreTags":["house"],"vibeTags":["warm"],"durationSec":300
                  },
                  {
                    "id":"t2","title":"B","artist":"DJ","bpm":123,"key":"9A","energy":0.40,
                    "danceability":0.7,"genreTags":["house"],"vibeTags":["groovy"],"durationSec":320
                  },
                  {
                    "id":"t3","title":"C","artist":"DJ","bpm":124,"key":"9B","energy":0.50,
                    "danceability":0.8,"genreTags":["house"],"vibeTags":["peak"],"durationSec":340
                  }
                ]
                """);

        SetPlan plan = planService.plan(jsonFile.toString(), "house", "rise", 2);

        assertEquals(2, plan.getOrderedTracks().size());
        assertEquals(1, plan.getTransitions().size());
    }

    @Test
    void planRequiresInputPath() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> planService.plan(" ", "house", "rise", 2)
        );
        assertTrue(ex.getMessage().contains("Input path is required"));
    }
}
