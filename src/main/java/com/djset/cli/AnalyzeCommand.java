package com.djset.cli;

import com.djset.service.AnalyzeService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "analyze", description = "Pre-analyze tracks (stub).")
public class AnalyzeCommand implements Runnable {
    private final AnalyzeService analyzeService = new AnalyzeService();

    @Option(
            names = {"--input-dir", "--music-dir"},
            description = "Input directory containing audio files."
    )
    private String inputDir;

    @Option(names = "--output", description = "Output JSON file path.")
    private String output;

    @Option(names = "--default-genre", description = "Optional default genre tag for tracks without genre metadata.")
    private String defaultGenre;

    @Option(names = "--python-cmd", description = "Python command used to run analyzer script (default: python).")
    private String pythonCmd = "python";

    @Option(
            names = "--analyzer-script",
            description = "Path to Python analyzer script (default: python/audio_analyzer.py)."
    )
    private String analyzerScript = "python/audio_analyzer.py";

    @Option(
            names = "--analyze-workers",
            description = "Number of parallel workers for file analysis (default: 1)."
    )
    private int analyzeWorkers = 1;

    @Option(
            names = "--mode",
            description = "Analyzer mode: standard | rekordbox-like (default: standard)."
    )
    private String mode = "standard";

    @Option(
            names = "--reanalyze",
            description = "Ignore cached analysis and recompute all files."
    )
    private boolean reanalyze;

    @Option(
            names = "--check-setup",
            description = "Check Python/script/dependencies and exit without analyzing files."
    )
    private boolean checkSetup;

    @Override
    public void run() {
        AnalyzeService.AnalyzerEnvironmentStatus status =
                analyzeService.checkAnalyzerEnvironment(pythonCmd, analyzerScript);
        printSetupStatus(status);
        if (checkSetup) {
            return;
        }
        requireAnalyzeArguments();
        int count = analyzeService.analyze(
                inputDir,
                output,
                defaultGenre,
                pythonCmd,
                analyzerScript,
                analyzeWorkers,
                mode,
                reanalyze
        );
        System.out.println("analyze: wrote " + count + " tracks to " + output);
    }

    private void requireAnalyzeArguments() {
        if (inputDir == null || inputDir.isBlank()) {
            throw new IllegalArgumentException("--input-dir/--music-dir is required unless --check-setup is used.");
        }
        if (output == null || output.isBlank()) {
            throw new IllegalArgumentException("--output is required unless --check-setup is used.");
        }
    }

    private void printSetupStatus(AnalyzeService.AnalyzerEnvironmentStatus status) {
        System.out.println("analyze setup:");
        System.out.println("  python: " + status.resolvedPythonCommand());
        System.out.println("  pythonAvailable: " + status.pythonAvailable());
        System.out.println("  analyzerScriptExists: " + status.analyzerScriptExists());
        System.out.println("  dependenciesReady(librosa,numpy): " + status.dependenciesReady());
        System.out.println("  ready: " + status.ready());
    }
}
