package com.djset.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyzeServiceJsonPayloadTest {

    @Test
    void extractsJsonWhenWarningsPrecedeSingleLineOutput() {
        String json = "{\"bpm\":128.0,\"tempoConfidence\":0.5,\"key\":\"8A\",\"keyConfidence\":0.9}";
        String out = "C:\\lib\\something: UserWarning: noisy\n" + json + "\n";
        String got = AnalyzeService.extractAnalyzerJsonPayload(out);
        assertEquals(json, got);
    }

    @Test
    void returnsFullStringWhenItIsAlreadyValidJson() {
        String json = "{\"bpm\":120.0,\"energy\":0.5}";
        assertEquals(json, AnalyzeService.extractAnalyzerJsonPayload(json));
    }

    @Test
    void stripsBom() {
        String json = "{\"bpm\":120.0}";
        assertEquals(json, AnalyzeService.extractAnalyzerJsonPayload("\uFEFF" + json));
    }

    @Test
    void picksLastParsableLineWhenMultipleJsonLikeLinesExist() {
        String good = "{\"bpm\":130.0,\"tempoConfidence\":1.0}";
        String out = "not json\n{\"broken\":\n" + good;
        assertEquals(good, AnalyzeService.extractAnalyzerJsonPayload(out));
    }

    @Test
    void blankReturnsEmpty() {
        assertEquals("", AnalyzeService.extractAnalyzerJsonPayload(""));
        assertEquals("", AnalyzeService.extractAnalyzerJsonPayload("   \n  "));
    }

    @Test
    void tailBraceSliceParsesMultilinePrettyPrintedJson() {
        String pretty = "ignored\n{\n  \"bpm\" : 125.0,\n  \"energy\" : 0.4\n}\n";
        String got = AnalyzeService.extractAnalyzerJsonPayload(pretty);
        assertTrue(got.contains("\"bpm\""));
        assertTrue(got.contains("125.0"));
    }
}
