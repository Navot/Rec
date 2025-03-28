package pkg.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pkg.Plan;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/plan")
public class PlanController {
    private final PlanService planService;

    @Autowired
    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping("/current")
    public ResponseEntity<?> getCurrentPlanId() {
        String currentPlanId = planService.getCurrentPlanId();
        
        if (currentPlanId == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, String> response = new HashMap<>();
        response.put("planId", currentPlanId);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{planId}")
    public ResponseEntity<Plan> getPlan(@PathVariable String planId) {
        Plan plan = planService.getPlan(planId);
        
        if (plan == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(plan);
    }

    @GetMapping("/list")
    public ResponseEntity<?> listPlans() {
        return ResponseEntity.ok(planService.listAllPlanIds());
    }
} 