package com.djset.ui;

import com.djset.ui.dto.ClearCacheRequest;
import com.djset.util.JsonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code POST /api/clear-cache} — delete {@code ~/.djset/analyze-cache/&lt;hash&gt;/} for a music folder.
 */
public final class ClearAnalyzeCacheApiHandler implements HttpHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, errorBody("Method not allowed"));
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.isBlank()) {
            sendJson(ex, 400, errorBody("Empty body"));
            return;
        }
        try {
            ClearCacheRequest req = MAPPER.readValue(body, ClearCacheRequest.class);
            if (req.musicDir == null || req.musicDir.isBlank()) {
                throw new IllegalArgumentException("musicDir is required.");
            }
            Path musicPath = Path.of(req.musicDir.trim()).toAbsolutePath().normalize();
            Map<String, Object> cleared = UiAnalyzeCache.clearLibraryCache(musicPath);
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("ok", true);
            ok.put("musicDir", musicPath.toString());
            ok.putAll(cleared);
            sendJson(ex, 200, ok);
        } catch (IllegalArgumentException e) {
            sendJson(ex, 400, errorBody(e.getMessage()));
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            sendJson(ex, 500, errorBody(msg));
        }
    }

    private static Map<String, Object> errorBody(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", message == null ? "Unknown error" : message);
        return m;
    }

    private static void sendJson(HttpExchange ex, int status, Object payload) throws IOException {
        byte[] bytes = JsonUtil.toJson(payload).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
