package com.djset.ui;

import com.djset.service.WebNextSongsService;
import com.djset.ui.dto.NextSongsRequest;
import com.djset.util.JsonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code POST /api/next-songs} — analyze folder + ranked next tracks for the interactive UI.
 */
public final class NextSongsApiHandler implements HttpHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final WebNextSongsService webNextSongsService = new WebNextSongsService();

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
            NextSongsRequest req = MAPPER.readValue(body, NextSongsRequest.class);
            Map<String, Object> result = webNextSongsService.run(req);
            sendJson(ex, 200, result);
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

    private static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = JsonUtil.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
