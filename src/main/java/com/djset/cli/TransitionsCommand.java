package com.djset.cli;

import com.djset.service.TransitionsService;
import picocli.CommandLine.Command;

@Command(name = "transitions", description = "Inspect transitions (stub).")
public class TransitionsCommand implements Runnable {
    private final TransitionsService transitionsService = new TransitionsService();

    @Override
    public void run() {
        transitionsService.listTransitions();
        System.out.println("transitions: stub");
    }
}
