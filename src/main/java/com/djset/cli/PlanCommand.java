package com.djset.cli;

import com.djset.model.SetPlan;
import com.djset.service.PlanService;
import com.djset.util.JsonUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "plan", description = "Generate a set plan from track JSON input.")
public class PlanCommand implements Runnable {
    private final PlanService planService = new PlanService();

    @Option(names = "--input", required = true, description = "Input JSON file path.")
    private String input;

    @Option(names = "--style", description = "Optional style label for output plan.")
    private String style;

    @Option(names = "--target-curve", description = "Optional target energy curve label.")
    private String targetCurve;

    @Option(names = "--count", description = "Number of songs to recommend (1-10).")
    private Integer count;

    @Override
    public void run() {
        SetPlan plan = planService.plan(input, style, targetCurve, count);
        System.out.println(JsonUtil.toJson(plan));
    }
}
