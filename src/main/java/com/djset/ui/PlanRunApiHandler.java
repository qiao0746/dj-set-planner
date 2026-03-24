package com.djset.ui;

import com.djset.service.WebPlanService;
import com.djset.ui.dto.PlanRunRequest;
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
 * {@code POST /api/run} — analyze folder + shaped plans for the interactive UI.
 */
public final class PlanRunApiHandler implements HttpHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final WebPlanService webPlanService = new WebPlanService();

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
            PlanRunRequest req = MAPPER.readValue(body, PlanRunRequest.class);
            Map<String, Object> result = webPlanService.run(req);
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
