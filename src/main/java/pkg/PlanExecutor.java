package pkg;

import com.google.gson.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class PlanExecutor {
    static JsonObject prompts;

    public static void main(String[] args) {
        prompts = getPrompts();
        if (prompts == null)
            return;
        Plan plan = createInitialPlan();
        executePlan(plan);
    }

    private static JsonObject getPrompts() {
        Gson gson = new Gson();
        JsonObject jsonObject = null;

        // First try to load from classpath resources
        try (InputStream inputStream = PlanExecutor.class.getResourceAsStream("/prompts.json")) {
            if (inputStream != null) {
                Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                jsonObject = gson.fromJson(reader, JsonObject.class);
                System.out.println("Prompts loaded from resources");
                return jsonObject;
            }
        } catch (IOException e) {
            System.out.println("Failed to load prompts from resources: " + e.getMessage());
        }

        // Fallback to file system
        try (Reader reader = new InputStreamReader(
                new java.io.FileInputStream("src/main/resources/prompts.json"), StandardCharsets.UTF_8)) {
            jsonObject = gson.fromJson(reader, JsonObject.class);
            System.out.println("Prompts loaded from file system");
            return jsonObject;
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Prompts is null");
            return null;
        }
    }

    private static Plan createInitialPlan() {
        String systemPrompt = prompts.getAsJsonObject("taskPlanning").get("system").getAsString();
        String userPrompt = "create an python server that will run on port 8081 with ui login. use admin admin as default.";
        JsonObject jsonScheme = prompts.getAsJsonObject("taskPlanning").getAsJsonObject("jsonScheme");
        try {
            JsonObject planJson = OllamaClient.queryOllamaWithSchema(systemPrompt, userPrompt, jsonScheme);

            Plan plan = new Plan();

            JsonArray tasksArray = planJson.getAsJsonArray("subtasks");
            for (int i = 0; i < tasksArray.size(); i++) {
                JsonObject taskJson = tasksArray.get(i).getAsJsonObject();
                Task task = parseTaskFromJson(taskJson);
                plan.addTask(task);
            }

            return plan;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void executePlan(Plan plan) {
        // Keep executing tasks until there are no more tasks to execute
        while (true) {
            // Always reevaluate the entire plan first
            PlanValidationResult validationResult = reevaluatePlan(plan);
            
            // Fix the plan if needed
            while (!validationResult.isValid()) {
                System.out.println("Plan is invalid. Reason: " + validationResult.getReason());
                fixPlan(plan, validationResult);
                validationResult = reevaluatePlan(plan);
            }
            
            // Get the next open task from the plan
            Task nextTask = findNextOpenTask(plan);
            
            // If there are no more tasks to execute, we're done
            if (nextTask == null) {
                System.out.println("Plan execution completed successfully.");
                break;
            }
            
            // Process the next task
            System.out.println("Executing task: " + nextTask.getDescription());
            ExecutionResult result = processTask(nextTask, plan);
            
            if (!result.isSuccess()) {
                System.out.println("Execution failed for [" + nextTask.getDescription() 
                    + "], reason: " + result.getMessage());
                
                // Reevaluate the plan after failure
                validationResult = reevaluatePlanWithResult(plan, result);
                
                                if (!validationResult.isValid()) {
                                    fixPlan(plan, validationResult);
                                } else {
                                    // If the plan is valid but the task failed, mark the task as complete
                                    // and continue with the next task
                                    markTaskAsCompleted(nextTask);
                                }
                            } else {
                                // Mark the task as completed
                                markTaskAsCompleted(nextTask);
                            }
                        }
                    }
                    
                    /**
     * Reevaluates the plan after a task execution resulted in either success or failure.
     * This allows the planner to consider the execution result when deciding how to proceed.
     * 
     * @param plan The current plan being executed
     * @param result The result of the last task execution
     * @return A PlanValidationResult indicating whether the plan is still valid
     */
    private static PlanValidationResult reevaluatePlanWithResult(Plan plan, ExecutionResult result) {
        String systemPrompt = prompts.getAsJsonObject("PlanReevaluation").get("system").getAsString();
        
        // Create a rich context that includes the execution result
        String userPrompt = "Evaluate the validity of the following plan in JSON format, considering that the last task execution " +
                            (result.isSuccess() ? "succeeded" : "failed") + " with the message: \"" + result.getMessage() + "\".\n" + 
                            planToJson(plan);
        
        JsonObject jsonScheme = prompts.getAsJsonObject("PlanReevaluation").getAsJsonObject("jsonScheme");
        
        try {
            JsonObject response = OllamaClient.queryOllamaWithSchema(systemPrompt, userPrompt, jsonScheme);

            boolean isValid = response.get("overallValidity").getAsBoolean();
            String reason = response.has("explanation") ? response.get("explanation").getAsString() : "";
            String improvements = response.has("improvements") ? response.get("improvements").getAsString() : "";
            
            // If the task failed but the plan is still valid, we might want to add additional information
            if (!result.isSuccess() && isValid) {
                reason += " Although the previous task failed, the plan structure remains valid.";
            }
            
            return new PlanValidationResult(isValid, reason, improvements);
        } catch (Exception e) {
            e.printStackTrace();
            return new PlanValidationResult(false, 
                "Error during plan reevaluation after " + (result.isSuccess() ? "successful" : "failed") + 
                " task execution: " + e.getMessage(), null);
        }
    }
    
    /**
     * Finds the next open task in the plan that has not been completed.
     * Implements a breadth-first search to find tasks in the order they appear in the plan.
     */
    private static Task findNextOpenTask(Plan plan) {
        // Queue for breadth-first search
        java.util.Queue<Task> queue = new java.util.LinkedList<>();
        
        // Add all top-level tasks to the queue
        for (Task task : plan.getTopLevelTasks()) {
            queue.add(task);
        }
        
        // Process tasks in breadth-first order
        while (!queue.isEmpty()) {
            Task current = queue.poll();
            
            // Skip completed tasks
            if (current.isCompleted()) {
                continue;
            }
            
            // If it's atomic, it's the next task to execute
            if (current.isAtomic()) {
                return current;
            }
            
            // If it's not atomic and has no subtasks, it needs to be broken down
            if (current.getSubTasks().isEmpty()) {
                return current;
            }
            
            // Add subtasks to the queue
            for (Task subtask : current.getSubTasks()) {
                queue.add(subtask);
            }
        }
        
        // No open tasks found
        return null;
    }
    
    /**
     * Marks a task as completed.
     */
    private static void markTaskAsCompleted(Task task) {
        task.setCompleted(true);
        System.out.println("Marked task as completed: " + task.getDescription());
    }
    

    /**
     * Recursively processes a task:
     * 1. If it's atomic, execute it directly.
     * 2. If it's non-atomic, break it down if needed, then process each subtask in
     * turn.
     *
     * Note we also reevaluate the ENTIRE plan after each subtask,
     * to see if global conditions have changed.
     */
    private static ExecutionResult processTask(Task task, Plan plan) {

        // Base case: Atomic => execute immediately
        if (task.isAtomic()) {
            System.out.println("Executing atomic task: " + task.getDescription());
            
            List<String> commands = task.getCommands();
            if (commands == null || commands.isEmpty()) {
                System.out.println("No commands to execute for task: " + task.getDescription());
                return new ExecutionResult(true, "No commands to execute, task considered complete.");
            }
            
            boolean allCommandsSucceeded = true;
            StringBuilder resultMessage = new StringBuilder();
            
            for (String command : commands) {
                System.out.println("Executing command: " + command);
                try {
                    // Create process builder with the command
                    ProcessBuilder processBuilder = new ProcessBuilder();
                    
                    // Set the command based on OS
                    if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                        processBuilder.command("cmd.exe", "/c", command);
                    } else {
                        processBuilder.command("sh", "-c", command);
                    }
                    
                    // Redirect error stream to output stream
                    processBuilder.redirectErrorStream(true);
                    
                    // Start the process
                    Process process = processBuilder.start();
                    
                    // Read the output
                    try (InputStream is = process.getInputStream();
                         InputStreamReader isr = new InputStreamReader(is);
                         java.io.BufferedReader br = new java.io.BufferedReader(isr)) {
                        
                        String line;
                        while ((line = br.readLine()) != null) {
                            System.out.println(line);
                        }
                    }
                    
                    // Wait for the process to complete and get exit code
                    int exitCode = process.waitFor();
                    
                    // Check if command succeeded (exit code 0)
                    if (exitCode == 0) {
                        System.out.println("Command executed successfully with exit code: " + exitCode);
                        resultMessage.append("Command '").append(command).append("' executed successfully.\n");
                    } else {
                        System.out.println("Command failed with exit code: " + exitCode);
                        resultMessage.append("Command '").append(command).append("' failed with exit code: ").append(exitCode).append(".\n");
                        allCommandsSucceeded = false;
                        break; // Stop executing remaining commands on failure
                    }
                    
                } catch (IOException | InterruptedException e) {
                    System.out.println("Error executing command: " + e.getMessage());
                    resultMessage.append("Error executing command '").append(command).append("': ").append(e.getMessage()).append(".\n");
                    allCommandsSucceeded = false;
                    break; // Stop executing remaining commands on failure
                }
            }
            
            // Evaluate the task based on success criteria if all commands succeeded
            if (allCommandsSucceeded) {
                String successCriteria = task.getSuccessCriteria();
                if (successCriteria != null && !successCriteria.isEmpty()) {
                    // Here we could implement more sophisticated success validation
                    // For now, we'll assume all commands executed successfully means criteria met
                    System.out.println("Success criteria met: " + successCriteria);
                }
                return new ExecutionResult(true, "Completed atomic task. " + resultMessage.toString());
            } else {
                return new ExecutionResult(false, "Failed to execute atomic task. " + resultMessage.toString());
            }
        }

        // If non-atomic, ensure subtasks are atomic or can be broken down
        if (!allSubTasksAtomic(task)) {
            // Attempt to break down
            breakDownTask(task);
            // Reevaluation step (the entire plan might shift after breakdown)
            PlanValidationResult valRes = reevaluatePlan(plan);
            if (!valRes.isValid()) {
                return new ExecutionResult(false, "pkg.Plan invalid after breakdown: " + valRes.getReason());
            }
        }

        // Process each subtask (recursively)
        for (Task sub : task.getSubTasks()) {
            // Skip already completed subtasks
            if (sub.isCompleted()) {
                continue;
            }
            
            ExecutionResult subResult = processTask(sub, plan);
            
            if (!subResult.isSuccess()) {
                return subResult;
            }
            
            // Mark the subtask as completed after successful execution
            sub.setCompleted(true);
            
            // Reevaluate after each subtask
            PlanValidationResult validation = reevaluatePlan(plan);
            if (!validation.isValid()) {
                return new ExecutionResult(false, "pkg.Plan invalid after subtask: " + validation.getReason());
            }
        }

        // If all subtasks have succeeded, mark this task as completed and return success
        task.setCompleted(true);
        return new ExecutionResult(true,
                "All subtasks of [" + task.getDescription() + "] completed successfully.");
    }

    /**
     * Identifies if all of a task's subtasks are atomic.
     */
    private static boolean allSubTasksAtomic(Task task) {
        for (Task sub : task.getSubTasks()) {
            if (!sub.isAtomic()) {
                return false;
            }
        }
        return true;
    }

    /**
     * A stub that 'breaks down' any non-atomic subtasks into atomic subtasks.
     * In a real system, this might involve user input, domain analysis, etc.
     */
    private static void breakDownTask(Task task) {
        String systemPrompt = "You are a task decomposition assistant.";
        String userPrompt = "Break down the following non-atomic task into atomic subtasks in JSON format:\n"
                + task.getDescription();

        try {
            String response = OllamaClient.queryOllama(systemPrompt, userPrompt);

            // Parse the JSON response
            JsonObject taskJson = JsonParser.parseString(response).getAsJsonObject();
            JsonArray subTasksArray = taskJson.getAsJsonArray("subtasks");

            for (int i = 0; i < subTasksArray.size(); i++) {
                JsonObject subTaskJson = subTasksArray.get(i).getAsJsonObject();
                Task subTask = parseTaskFromJson(subTaskJson);
                task.addSubTask(subTask);
            }

            task.setAtomic(true); // Mark the task as atomic after breakdown
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Reevaluates the entire plan.
     * For demo purposes, we'll do some trivial checks, but in a real system
     * you would have logic to ensure the plan is still valid after each step.
     */
    private static PlanValidationResult reevaluatePlan(Plan plan) {
        String systemPrompt = prompts.getAsJsonObject("PlanReevaluation").get("system").getAsString();
        String userPrompt = "Evaluate the validity of the following plan in JSON format:\n" + planToJson(plan);
        JsonObject jsonScheme = prompts.getAsJsonObject("PlanReevaluation").getAsJsonObject("jsonScheme");
        try {
            JsonObject response = OllamaClient.queryOllamaWithSchema(systemPrompt, userPrompt, jsonScheme);

            boolean isValid = response.get("overallValidity").getAsBoolean();
            String reason = response.has("explanation") ? response.get("explanation").getAsString() : "";
            String improvements = response.has("improvements") ? response.get("improvements").getAsString() : "";
            return new PlanValidationResult(isValid, reason, improvements);
        } catch (Exception e) {
            e.printStackTrace();
            return new PlanValidationResult(false, "Error during plan reevaluation.", null);
        }
    }

    /**
     * If the plan is invalid (or partially invalid), fix or modify it here.
     * This is a stub – real logic might remove or reorder tasks, prompt user, etc.
     */
    private static void fixPlan(Plan plan, PlanValidationResult validationResult) {
        String systemPrompt = prompts.getAsJsonObject("planEditor").get("system").getAsString();
        String userPrompt = "The following plan has been deemed invalid for the following reason: "
                + validationResult.getReason()
                + "\nPlease provide a corrected plan in JSON format:\n"
                + planToJson(plan);
        JsonObject jsonScheme = prompts.getAsJsonObject("planEditor").getAsJsonObject("jsonScheme");

        try {
            JsonObject response = OllamaClient.queryOllamaWithSchema(systemPrompt, userPrompt, jsonScheme);

            // Parse the JSON response
            JsonObject fixCommands = response.getAsJsonObject("commands");
            PlanEditor planEditor = new PlanEditor(plan);
            planEditor.executeCommands(fixCommands);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Task parseTaskFromJson(JsonObject taskJson) {
        Gson gson = new Gson();
        Task task = gson.fromJson(taskJson, Task.class);
        task.initSubTasks();

        if (taskJson.has("subtasks")) {
            JsonArray subTasksArray = taskJson.getAsJsonArray("subtasks");
            for (int i = 0; i < subTasksArray.size(); i++) {
                JsonObject subTaskJson = subTasksArray.get(i).getAsJsonObject();
                Task subTask = parseTaskFromJson(subTaskJson);
                task.addSubTask(subTask);
            }
        }
        return task;
    }

    private static String planToJson(Plan plan) {
        JsonObject planJson = new JsonObject();
        JsonArray tasksArray = new JsonArray();

        for (Task task : plan.getTopLevelTasks()) {
            tasksArray.add(taskToJson(task));
        }

        planJson.add("tasks", tasksArray);
        return planJson.toString();
    }

    private static JsonObject taskToJson(Task task) {
        Gson gson = new Gson();

        JsonElement jsonElement = gson.toJsonTree(task);

        JsonObject taskJson = jsonElement.getAsJsonObject();

        if (!task.getSubTasks().isEmpty()) {
            JsonArray subTasksArray = new JsonArray();
            for (Task subTask : task.getSubTasks()) {
                subTasksArray.add(taskToJson(subTask));
            }
            taskJson.add("subtasks", subTasksArray);
        }
        return taskJson;
    }

    public static String prettyPrintJsonArray(JsonArray jsonArray) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(jsonArray);
    }

    public static String prettyPrintJsonObject(JsonObject jsonObject) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(jsonObject);
    }

}