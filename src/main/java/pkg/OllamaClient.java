package pkg;

import com.google.gson.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OllamaClient {

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

        // Optionally print the request (or remove later)
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        System.out.println(gson.toJson(messages));

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
        System.out.println(gson.toJson(jsonResponse));

        JsonObject message = jsonResponse.getAsJsonObject("message");
        return message.get("content").getAsString();
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


        JsonObject jsonResponse;

        try {
            jsonResponse = JsonParser.parseString(responseTextClean).getAsJsonObject();
        } catch(Exception e) {
            // If the response isn't valid JSON, ask the agent to fix it.
            return fixJsonResponse(systemPrompt, expectedSchema);
        }

        // Loop until the JSON matches the expected schema.
        while (!matchesSchema(jsonResponse, expectedSchema)) {
            // Construct a prompt asking the agent to adjust the JSON to match the expected schema.
            String fixPrompt = "The response does not match the expected JSON schema: "
                    + expectedSchema.toString() + ". Please reformat your response to exactly follow this schema.";
            String fixedResponseText = queryOllama(systemPrompt, fixPrompt);
            String fixedResponseTextClean = extracted(fixedResponseText);
            try {
                jsonResponse = JsonParser.parseString(fixedResponseTextClean).getAsJsonObject();
            } catch(Exception e) {
                // If the new response is still not valid JSON, continue the loop.
                continue;
            }
        }
        return jsonResponse;
    }

    private static String extracted(String rawText) {
        Pattern pattern = Pattern.compile("```json\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(rawText);

        if (matcher.find()) {
            // Extract the JSON string (group 1)
            String jsonText = matcher.group(1);
            System.out.println("Extracted JSON:");
            System.out.println(jsonText);

            return jsonText;
        } else {
            System.out.println("No JSON code block found in the input text.");
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