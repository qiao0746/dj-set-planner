package com.djset.service;

import com.djset.scorer.TransitionScoreResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MixSuggestionTest {

    @Test
    void bandsMatchPlannerCopy() {
        assertTrue(MixSuggestion.fromTotalScore(7.0).contains("Long blend"));
        assertTrue(MixSuggestion.fromTotalScore(5.0).contains("Standard blend"));
        assertTrue(MixSuggestion.fromTotalScore(1.0).contains("Quick"));
    }

    @Test
    void fromTransitionScoreUsesTotal() {
        var tr = new TransitionScoreResult(6.5, 0, 0, 0, 0, List.of());
        assertTrue(MixSuggestion.fromTransitionScore(tr).contains("Long blend"));
    }
}
