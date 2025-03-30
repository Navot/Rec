package pkg;

import com.google.gson.*;
import pkg.web.LogService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OllamaClient {

    private static LogService logService;

    // Setter for the logService (to be injected)
    public static void setLogService(LogService service) {
        logService = service;
    }

    // Your original query function.
    static String queryOllama(String systemPrompt, String userPrompt) throws Exception {
        URL url = new URL("http://localhost:11434/api/chat");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "phi4:latest"); // Replace with your model

        JsonArray messages = new JsonArray();

        // Add system prompt
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);
        messages.add(systemMessage);

        // Add user prompt
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", userPrompt);
        messages.add(userMessage);

        requestBody.add("messages", messages);
        requestBody.addProperty("stream", false); // Set to true if streaming responses are desired

        // Log user prompt
        String userPromptMessage = messages.get(1).getAsJsonObject().get("content").getAsString();
        if (logService != null) {
            logService.addLlmRequest("USER PROMPT: " + userPromptMessage);
        } else {
            System.out.println("USER PROMPT: " + userPromptMessage);
        }

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.toString().getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        int code = connection.getResponseCode();
        if (code != 200) {
            throw new RuntimeException("Failed : HTTP error code : " + code);
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            response.append(line.trim());
        }
        in.close();

        JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();

        JsonObject message = jsonResponse.getAsJsonObject("message");
        String assistantResponse = message.get("content").getAsString();
        
        // Log assistant response
        if (logService != null) {
            logService.addLlmResponse("ASSISTANT RESPONSE: " + assistantResponse);
        } else {
            System.out.println("ASSISTANT PROMPT: " + assistantResponse);
        }
        
        return assistantResponse;
    }

    /**
     * Checks if the response JSON contains all keys defined in the expected schema.
     * (You might want to enhance this check to include type validation or nested structure checks.)
     */

    private static boolean matchesSchema(JsonElement response, JsonElement schema) {
        // If the schema is an object, then the response must be an object.
        if (schema.isJsonObject()) {
            if (!response.isJsonObject()) return false;
            JsonObject schemaObj = schema.getAsJsonObject();
            JsonObject responseObj = response.getAsJsonObject();
            for (String key : schemaObj.keySet()) {
                if (!responseObj.has(key)) {
                    return false;
                }
                // Recursively validate each key.
                if (!matchesSchema(responseObj.get(key), schemaObj.get(key))) {
                    return false;
                }
            }
            return true;
        }
        // If the schema is an array, then the response must be an array.
        else if (schema.isJsonArray()) {
            if (!response.isJsonArray()) return false;
            JsonArray schemaArr = schema.getAsJsonArray();
            JsonArray responseArr = response.getAsJsonArray();
            // If the schema array has elements, use the first element as the expected structure for each item.
            if (schemaArr.size() > 0) {
                JsonElement schemaElement = schemaArr.get(0);
                for (JsonElement item : responseArr) {
                    if (!matchesSchema(item, schemaElement)) {
                        return false;
                    }
                }
            }
            return true;
        }
        // For primitives, you might want to validate the type.
        // Here we assume a simple check: if the expected schema is a string, then the response should be a primitive.
        else if (schema.isJsonPrimitive()) {
            if (!response.isJsonPrimitive()) return false;
            // Optionally, you can implement type comparisons here.
            // For example, if schema.getAsString() contains "string", "number", or "boolean", validate accordingly.
            // In this simple example, we just assume it's valid if both are primitives.
            return true;
        }
        return false;
    }

    /**
     * Queries Ollama and attempts to map the response to the expected JSON schema.
     * If the mapping fails, sends a follow-up message asking the agent to fix the JSON.
     *
     * @param systemPrompt   The system prompt.
     * @param userPrompt     The initial user prompt.
     * @param expectedSchema The JSON schema that the final response must follow.
     * @return The JSON response mapped to the expected schema.
     * @throws Exception If an error occurs during the query.
     */
    public static JsonObject queryOllamaWithSchema(String systemPrompt, String userPrompt, JsonObject expectedSchema) throws Exception {
        String responseText = queryOllama(systemPrompt, userPrompt);
        String responseTextClean = extracted(responseText);

        if (responseTextClean == null) {
            // If extraction failed, try to find JSON anywhere in the response
            responseTextClean = extractAnyJson(responseText);
            if (responseTextClean == null) {
                System.out.println("No valid JSON found in response. Original response:\n" + responseText);
                responseTextClean = "{}";  // Provide an empty object to avoid NPE
            }
        }

        JsonObject jsonResponse;

        try {
            jsonResponse = JsonParser.parseString(responseTextClean).getAsJsonObject();
        } catch(Exception e) {
            System.out.println("Failed to parse response as JSON: " + e.getMessage());
            System.out.println("Original response text: " + responseText);
            System.out.println("Cleaned response text: " + responseTextClean);
            // If the response isn't valid JSON, ask the agent to fix it.
            jsonResponse = fixJsonResponse(systemPrompt, expectedSchema);
        }

        // Check for task planning specific needs (subtasks array)
        if (expectedSchema.has("subtasks") && !jsonResponse.has("subtasks")) {
            System.out.println("Response is missing required 'subtasks' array. Attempting to fix...");
            
            // Look for arrays that might contain tasks
            for (String key : jsonResponse.keySet()) {
                if (jsonResponse.get(key).isJsonArray()) {
                    JsonArray array = jsonResponse.getAsJsonArray(key);
                    if (array.size() > 0 && array.get(0).isJsonObject()) {
                        // Check if the first element looks like a task
                        JsonObject firstItem = array.get(0).getAsJsonObject();
                        if (firstItem.has("description") || firstItem.has("id")) {
                            System.out.println("Found potential tasks array in key: " + key);
                            jsonResponse.add("subtasks", array);
                            break;
                        }
                    }
                }
            }
            
            // If we still don't have subtasks, try to extract from the raw response
            if (!jsonResponse.has("subtasks")) {
                JsonArray subtasks = extractTasksFromText(responseText);
                if (subtasks != null && subtasks.size() > 0) {
                    jsonResponse.add("subtasks", subtasks);
                    System.out.println("Extracted " + subtasks.size() + " tasks from raw text");
                } else {
                    // Create a minimal valid response with empty subtasks array
                    JsonArray emptySubtasks = new JsonArray();
                    jsonResponse.add("subtasks", emptySubtasks);
                    System.out.println("Created empty subtasks array as fallback");
                }
            }
        }

        // Handle commands schema requirements
        if (expectedSchema.has("commands") && !jsonResponse.has("commands")) {
            // Make sure the response has a commands property
            // Look for arrays that might contain commands
            for (String key : jsonResponse.keySet()) {
                if (jsonResponse.get(key).isJsonArray()) {
                    // Found an array, check if it contains strings that look like commands
                    JsonArray array = jsonResponse.getAsJsonArray(key);
                    boolean looksLikeCommands = true;
                    
                    for (JsonElement element : array) {
                        if (!element.isJsonPrimitive() || !element.getAsString().contains("(")) {
                            looksLikeCommands = false;
                            break;
                        }
                    }
                    
                    if (looksLikeCommands) {
                        // Create a new object with the commands array
                        jsonResponse.add("commands", array);
                        System.out.println("Found commands array in key: " + key);
                        break;
                    }
                }
            }
            
            // If we haven't found a commands array, create a wrapper
            if (!jsonResponse.has("commands")) {
                JsonArray commandsArray = new JsonArray();
                jsonResponse.add("commands", commandsArray);
                System.out.println("Creating empty commands wrapper");
            }
        }

        // If we have a commands property, make sure it's an array
        if (jsonResponse.has("commands") && !jsonResponse.get("commands").isJsonArray()) {
            String commandStr = jsonResponse.get("commands").getAsString();
            JsonArray commandsArray = new JsonArray();
            
            // Check if it's a single command string
            if (commandStr.contains("(") && commandStr.contains(")")) {
                commandsArray.add(commandStr);
                jsonResponse.add("commands", commandsArray);
                System.out.println("Converting single command string to array");
            } else {
                // Replace with empty array
                jsonResponse.add("commands", new JsonArray());
            }
        }

        return jsonResponse;
    }

    /**
     * Print to original console output without timestamps
     */
    private static void printRawToConsole(String message) {
        try {
            // Try to get the ConsoleRedirector instance
            pkg.web.ConsoleRedirector redirector = pkg.web.ConsoleRedirector.getInstance();
            if (redirector != null && redirector.originalOut != null) {
                redirector.originalOut.println(message);
                return;
            }
        } catch (Exception e) {
            // Fall back to normal System.out if we can't get the original
            System.out.println("(Unable to print without timestamps) " + message);
        }
    }

    private static String extracted(String rawText) {
        Pattern pattern = Pattern.compile("```json\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(rawText);

        if (matcher.find()) {
            // Extract the JSON string (group 1)
            String jsonText = matcher.group(1);
            System.out.println("Extracted JSON:");
            
            // Format and print JSON without timestamps
            try {
                JsonObject jsonObject = JsonParser.parseString(jsonText).getAsJsonObject();
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String prettyJson = gson.toJson(jsonObject);
                
                // Try to get the LogService instance from Spring context
                try {
                    pkg.web.LogService logService = getLogService();
                    if (logService != null) {
                        // Use the special method to log JSON without timestamps
                        logService.addRawJson(prettyJson);
                    } else {
                        // Fall back to direct printing
                        printRawToConsole("\n--- JSON OUTPUT START ---\n" + prettyJson + "\n--- JSON OUTPUT END ---\n");
                    }
                } catch (Exception e) {
                    // Fall back to direct printing
                    printRawToConsole("\n--- JSON OUTPUT START ---\n" + prettyJson + "\n--- JSON OUTPUT END ---\n");
                }
            } catch (Exception e) {
                // If JSON parsing fails, print the raw text
                System.out.println(jsonText);
            }

            return jsonText;
        } else {
            System.out.println("No JSON code block found in the input text.");
        }
        return null;
    }

    /**
     * Get the LogService instance if available
     */
    private static pkg.web.LogService getLogService() {
        try {
            // Try to get LogService from PlanExecutor
            Class<?> executorClass = Class.forName("pkg.PlanExecutor");
            for (java.lang.reflect.Field field : executorClass.getDeclaredFields()) {
                if (field.getType().getName().equals("pkg.web.LogService")) {
                    field.setAccessible(true);
                    Object instance = null;
                    
                    // Try to get a PlanExecutor instance from Spring
                    if (Class.forName("pkg.PlanExecutorApplication") != null) {
                        Object context = Class.forName("pkg.PlanExecutorApplication")
                            .getDeclaredMethod("getApplicationContext").invoke(null);
                        if (context != null) {
                            instance = context.getClass().getMethod("getBean", Class.class)
                                .invoke(context, executorClass);
                        }
                    }
                    
                    if (instance != null) {
                        return (pkg.web.LogService) field.get(instance);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore any errors
        }
        return null;
    }

    /**
     * Extract any JSON object from the text
     */
    private static String extractAnyJson(String rawText) {
        // Try to extract any JSON object from { to matching }
        Pattern pattern = Pattern.compile("\\{.*?\\}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(rawText);
        
        if (matcher.find()) {
            String jsonText = matcher.group(0);
            System.out.println("Extracted potential JSON:");
            
            // Format and print as a single block to avoid timestamps on each line
            try {
                JsonObject jsonObject = JsonParser.parseString(jsonText).getAsJsonObject();
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String prettyJson = gson.toJson(jsonObject);
                
                // Try to get the LogService instance from Spring context
                try {
                    pkg.web.LogService logService = getLogService();
                    if (logService != null) {
                        // Use the special method to log JSON without timestamps
                        logService.addRawJson(prettyJson);
                    } else {
                        // Fall back to direct printing
                        printRawToConsole("\n--- JSON OUTPUT START ---\n" + prettyJson + "\n--- JSON OUTPUT END ---\n");
                    }
                } catch (Exception e) {
                    // Fall back to direct printing
                    printRawToConsole("\n--- JSON OUTPUT START ---\n" + prettyJson + "\n--- JSON OUTPUT END ---\n");
                }
                
                return jsonText;
            } catch (Exception e) {
                // If JSON parsing fails, print the raw text
                System.out.println(jsonText);
                System.out.println("Extracted text is not valid JSON: " + e.getMessage());
            }
        }
        
        return null;
    }

    /**
     * Fallback method in case the initial response isn't valid JSON.
     * Asks the agent to output a JSON that fits the expected schema.
     */
    private static JsonObject fixJsonResponse(String systemPrompt, JsonObject expectedSchema) throws Exception {
        String fixPrompt = "The response was not valid JSON. Please output a JSON following this schema: " + expectedSchema.toString();
        String fixedResponseText = queryOllama(systemPrompt, fixPrompt);
        String fixedResponseTextClean = extracted(fixedResponseText);
        
        if (fixedResponseTextClean == null) {
            // If no JSON block is found, try to parse the entire response
            System.out.println("No JSON code block found, attempting to parse the entire response");
            try {
                return JsonParser.parseString(fixedResponseText).getAsJsonObject();
            } catch (Exception e) {
                System.out.println("Failed to parse the entire response: " + e.getMessage());
                throw new RuntimeException("Unable to extract valid JSON from the response", e);
            }
        }
        
        return JsonParser.parseString(fixedResponseTextClean).getAsJsonObject();
    }

    /**
     * Try to extract task information from raw text when JSON parsing fails
     */
    private static JsonArray extractTasksFromText(String rawText) {
        JsonArray tasks = new JsonArray();
        
        // Split the text by lines or other delimiters that might indicate tasks
        String[] lines = rawText.split("\n");
        
        int taskId = 1;
        StringBuilder currentTask = new StringBuilder();
        boolean inTask = false;
        
        for (String line : lines) {
            line = line.trim();
            
            // Look for patterns like "Task 1:", "Step 1:", "1. ", etc.
            if (line.matches("^(Task|Step|\\d+\\.?)\\s+.*")) {
                // If we were already building a task, save the previous one
                if (inTask && currentTask.length() > 0) {
                    JsonObject task = new JsonObject();
                    task.addProperty("id", taskId++);
                    task.addProperty("description", currentTask.toString().trim());
                    task.addProperty("isAtomic", true);
                    JsonArray commands = new JsonArray();
                    commands.add("echo 'Executing: " + currentTask.toString().trim().replace("'", "\\'") + "'");
                    task.add("commands", commands);
                    tasks.add(task);
                    
                    currentTask = new StringBuilder();
                }
                
                inTask = true;
                currentTask.append(line.replaceFirst("^(Task|Step|\\d+\\.?)\\s+", ""));
            } 
            // Continue building the current task description
            else if (inTask) {
                currentTask.append(" ").append(line);
            }
        }
        
        // Don't forget the last task
        if (inTask && currentTask.length() > 0) {
            JsonObject task = new JsonObject();
            task.addProperty("id", taskId);
            task.addProperty("description", currentTask.toString().trim());
            task.addProperty("isAtomic", true);
            JsonArray commands = new JsonArray();
            commands.add("echo 'Executing: " + currentTask.toString().trim().replace("'", "\\'") + "'");
            task.add("commands", commands);
            tasks.add(task);
        }
        
        return tasks;
    }

    // For testing purposes
    public static void main(String[] args) {
        try {
            // Example system and user prompts.
            String systemPrompt = "You are a helpful assistant.";
            String userPrompt = "Provide your response in the following JSON format.";

            // Define an expected JSON schema.
            // For instance, we expect the JSON to contain "title" and "description".
            JsonObject expectedSchema = new JsonObject();
            expectedSchema.addProperty("title", "");
            expectedSchema.addProperty("description", "");

            JsonObject result = queryOllamaWithSchema(systemPrompt, userPrompt, expectedSchema);
            System.out.println("Final mapped JSON: " + result.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}