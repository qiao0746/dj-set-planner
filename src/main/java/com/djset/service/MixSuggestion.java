package com.djset.service;

import com.djset.scorer.TransitionScoreResult;

/**
 * DJ-facing blend hint from the same transition total score bands as {@link SetPlannerService}.
 */
public final class MixSuggestion {

    private static final double LONG_BLEND_THRESHOLD = 6.0;
    private static final double STANDARD_BLEND_THRESHOLD = 3.0;

    private MixSuggestion() {
    }

    /**
     * Uses the <em>base</em> transition total (BPM + key + energy + style), not intent-adjusted scores.
     */
    public static String fromTransitionScore(TransitionScoreResult scoreResult) {
        if (scoreResult == null) {
            return "Quick transition recommended.";
        }
        return fromTotalScore(scoreResult.getTotalScore());
    }

    public static String fromTotalScore(double totalScore) {
        if (totalScore >= LONG_BLEND_THRESHOLD) {
            return "Long blend, phrased 16-32 bars.";
        }
        if (totalScore >= STANDARD_BLEND_THRESHOLD) {
            return "Standard blend, 8-16 bars.";
        }
        return "Quick transition recommended.";
    }
}
