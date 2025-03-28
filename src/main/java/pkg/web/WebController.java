package pkg.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import pkg.Plan;
import pkg.PlanExecutor;

import java.util.HashMap;
import java.util.Map;

@Controller
public class WebController {

    private final PlanExecutor planExecutor;
    private final PlanService planService;
    private final LogService logService;
    private final FixLogFilter fixLogFilter;

    @Autowired
    public WebController(PlanExecutor planExecutor, PlanService planService, LogService logService) {
        this.planExecutor = planExecutor;
        this.planService = planService;
        this.logService = logService;
        this.fixLogFilter = new FixLogFilter();
    }

    /**
     * Home page mapping
     */
    @GetMapping("/")
    public String home() {
        return "index";
    }

    /**
     * Execute a default plan
     */
    @PostMapping("/api/execute")
    @ResponseBody
    public Map<String, Object> executeDefaultPlan() {
        planExecutor.executeWithDefaultPrompt();
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Execution started");
        return response;
    }

    /**
     * Execute a plan with a custom prompt
     */
    @PostMapping("/api/execute/custom")
    @ResponseBody
    public Map<String, Object> executeCustomPlan(@RequestParam String prompt) {
        planExecutor.executeWithCustomPrompt(prompt);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Custom execution started");
        return response;
    }
    
    /**
     * Get a summary of plan validation and fixes
     */
    @GetMapping("/api/plan-fixes/{planId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPlanFixesSummary(@PathVariable String planId) {
        Plan plan = planService.getPlan(planId);
        if (plan == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("planId", planId);
        summary.put("totalTasks", plan.getTopLevelTasks().size());
        summary.put("fixLogs", fixLogFilter.filterFixLogs(logService.getAllLogs()));
        
        return ResponseEntity.ok(summary);
    }
} 