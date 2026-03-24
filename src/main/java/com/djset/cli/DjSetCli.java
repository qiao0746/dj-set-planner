package com.djset.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "djset",
        description = "DJ set planning CLI.",
        mixinStandardHelpOptions = true,
        version = "djset 0.1.0",
        subcommands = {
                PlanCommand.class,
                AnalyzeCommand.class,
                TransitionsCommand.class,
                RecommendCommand.class,
                BuildSetCommand.class,
                BuildFromFirstCommand.class,
                ShapeFromFirstCommand.class,
                UiCommand.class
        }
)
public class DjSetCli implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new DjSetCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
