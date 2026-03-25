package com.djset.ui;

import com.djset.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * Serves {@code viewer/} (interactive planner + static viewer), {@code POST /api/run},
 * {@code POST /api/next-songs}, and {@code POST /api/clear-cache}.
 */
public final class UiServer {

    private UiServer() {
    }

    public static void main(String[] args) throws Exception {
        int port = 8787;
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }
        Path root = Path.of("viewer").toAbsolutePath().normalize();
        startAndBlock(root, port);
    }

    /**
     * Starts HTTP server and blocks until the JVM exits (e.g. Ctrl+C).
     *
     * @param viewerRoot directory containing {@code interactive/}, {@code ui/}, {@code djset-result-viewer.html}
     */
    public static void startAndBlock(Path viewerRoot, int port) throws IOException, InterruptedException {
        if (!Files.isDirectory(viewerRoot)) {
            throw new IOException("Viewer directory does not exist: " + viewerRoot + " (run from project root or pass --dir)");
        }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/run", new PlanRunApiHandler());
        server.createContext("/api/next-songs", new NextSongsApiHandler());
        server.createContext("/api/clear-cache", new ClearAnalyzeCacheApiHandler());
        server.createContext("/", new ViewerStaticHandler(viewerRoot));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println("djset UI: http://127.0.0.1:" + port + "/");
        System.out.println("Interactive: http://127.0.0.1:" + port + "/interactive/");
        System.out.println("Serving: " + viewerRoot);
        System.out.println("Press Ctrl+C to stop.");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        new CountDownLatch(1).await();
    }

    static final class ViewerStaticHandler implements HttpHandler {
        private final Path root;

        ViewerStaticHandler(Path root) {
            this.root = root;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                String path = ex.getRequestURI() != null ? ex.getRequestURI().getPath() : "";
                String message = "Method not allowed; static files are served with GET only.";
                if (path != null && path.startsWith("/api/")) {
                    message += " Restart the UI from the project root after updating so API routes (e.g. POST /api/next-songs) are registered.";
                }
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("ok", false);
                err.put("error", message);
                byte[] bytes = JsonUtil.toJson(err).getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                ex.sendResponseHeaders(405, bytes.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }
            String raw = ex.getRequestURI().getPath();
            if (raw == null || raw.isEmpty() || "/".equals(raw)) {
                raw = "/interactive/index.html";
            }
            if (raw.endsWith("/")) {
                raw = raw + "index.html";
            }
            if (raw.contains("..")) {
                ex.sendResponseHeaders(403, -1);
                ex.close();
                return;
            }
            Path file = root.resolve(raw.substring(1)).normalize();
            if (!file.startsWith(root)) {
                ex.sendResponseHeaders(403, -1);
                ex.close();
                return;
            }
            if (!Files.isRegularFile(file)) {
                ex.sendResponseHeaders(404, -1);
                ex.close();
                return;
            }
            byte[] bytes = Files.readAllBytes(file);
            ex.getResponseHeaders().set("Content-Type", mimeType(file.getFileName().toString()));
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }

        private static String mimeType(String name) {
            String n = name.toLowerCase();
            if (n.endsWith(".html")) {
                return "text/html; charset=utf-8";
            }
            if (n.endsWith(".css")) {
                return "text/css; charset=utf-8";
            }
            if (n.endsWith(".js")) {
                return "application/javascript; charset=utf-8";
            }
            if (n.endsWith(".json")) {
                return "application/json; charset=utf-8";
            }
            if (n.endsWith(".svg")) {
                return "image/svg+xml";
            }
            if (n.endsWith(".ico")) {
                return "image/x-icon";
            }
            return "application/octet-stream";
        }
    }
}
