package com.djset.cli;

import com.djset.ui.UiServer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

@Command(
        name = "ui",
        description = "Serve viewer + interactive planner at http://127.0.0.1:<port>/ (POST /api/run analyzes a folder)."
)
public class UiCommand implements Runnable {

    @Option(names = "--port", defaultValue = "8787", description = "HTTP listen port")
    int port;

    @Option(names = "--dir", description = "Viewer root: interactive/, ui/, etc. (default: ./viewer)")
    String dir;

    @Override
    public void run() {
        try {
            Path root = dir != null
                    ? Path.of(dir).toAbsolutePath().normalize()
                    : Path.of("viewer").toAbsolutePath().normalize();
            UiServer.startAndBlock(root, port);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
