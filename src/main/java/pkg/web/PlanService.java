package pkg.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.stereotype.Service;
import pkg.Plan;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlanService {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final String plansDir = "plans";
    private String currentPlanId;

    public PlanService() {
        // Ensure plans directory exists
        try {
            Files.createDirectories(Paths.get(plansDir));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create plans directory", e);
        }
    }

    /**
     * Save a plan to a file with a unique identifier
     */
    public String savePlan(Plan plan) {
        String planId = generatePlanId();
        currentPlanId = planId;
        
        try (FileWriter writer = new FileWriter(getPlanFilePath(planId))) {
            gson.toJson(plan, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save plan", e);
        }
        
        return planId;
    }

    /**
     * Get a plan by its ID
     */
    public Plan getPlan(String planId) {
        File planFile = new File(getPlanFilePath(planId));
        
        if (!planFile.exists()) {
            return null;
        }
        
        try (FileReader reader = new FileReader(planFile)) {
            return gson.fromJson(reader, Plan.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read plan", e);
        }
    }

    /**
     * Get the current active plan ID
     */
    public String getCurrentPlanId() {
        return currentPlanId;
    }

    /**
     * Set the current active plan ID
     */
    public void setCurrentPlanId(String planId) {
        this.currentPlanId = planId;
    }

    /**
     * Update an existing plan
     */
    public void updatePlan(String planId, Plan plan) {
        try (FileWriter writer = new FileWriter(getPlanFilePath(planId))) {
            gson.toJson(plan, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to update plan", e);
        }
    }

    /**
     * List all available plan IDs
     */
    public List<String> listAllPlanIds() {
        try {
            return Files.list(Paths.get(plansDir))
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - 5)) // Remove .json extension
                    .sorted(Comparator.reverseOrder()) // Most recent first
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    /**
     * Generate a unique plan ID based on timestamp
     */
    private String generatePlanId() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = LocalDateTime.now().format(formatter);
        return timestamp + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Get the file path for a plan ID
     */
    private String getPlanFilePath(String planId) {
        return plansDir + File.separator + planId + ".json";
    }
} 