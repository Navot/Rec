package pkg;

import com.google.gson.*;
import java.util.*;

public class PlanEditor {

    private Plan plan;
   
    public PlanEditor(Plan plan) {
        this.plan = plan;
    }

    /**
     * Recursively searches for a Task with the given id in the provided list.
     */
    private Task findTaskById(List<Task> tasks, int id) {
        for (Task task : tasks) {
            if (task.getId() == id) {
                return task;
            }
            Task found = findTaskById(task.getSubTasks(), id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Public method to retrieve a TaskEditor for the task with the given id.
     */
    public TaskEditor getTask(int id) {
        Task task = findTaskById(plan.getTopLevelTasks(), id);
        return (task != null) ? new TaskEditor(task) : null;
    }

    /**
     * Recursively removes the task with the given id from the provided list.
     */
    private boolean removeTaskById(List<Task> tasks, int id) {
        Iterator<Task> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            Task task = iterator.next();
            if (task.getId() == id) {
                iterator.remove();
                return true;
            }
            if (removeTaskById(task.getSubTasks(), id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes a task by id from the plan.
     */
    public void removeTask(int id) {
        removeTaskById(plan.getTopLevelTasks(), id);
    }

    /**
     * Adds a new task to the top-level tasks of the plan.
     */
    public void addTask(Task task) {
        plan.addTask(task);
    }

    /**
     * Updates a task with the given id using properties provided as a JsonObject.
     * Supported properties: description, successCriteria, commands, isAtomic, id.
     */
    public void updateTask(int id, JsonObject properties) {
        TaskEditor taskEditor = getTask(id);
        if (taskEditor != null) {
            for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
                String property = entry.getKey();
                JsonElement value = entry.getValue();
                taskEditor.change(property, value);
            }
        }
    }

    /**
     * Executes a list of commands provided in a JSON object.
     * The expected JSON format is:
     *
     * {
     *   "commands": [
     *      "getTask(2).change(\"description\", \"Update the HTML file creation command to include a meta viewport.\");",
     *      "getTask(3).appendCommand(\"echo 'background-color: #fff;' >> style.css\");",
     *      "removeTask(4);",
     *      "addTask({\"id\": 6, \"description\": \"Set up a custom 404 page\", \"commands\": [\"touch 404.html\", \"echo '<h1>Page Not Found</h1>' > 404.html\"], \"successCriteria\": \"'404.html' exists and displays a custom message when accessed.\", \"isAtomic\": true});",
     *      "updateTask(5, {\"commands\": [\"python3 -m http.server 8080\"], \"successCriteria\": \"Server starts and is accessible at 'http://localhost:8080/index.html'.\"});"
     *   ]
     * }
     */
    public void executeCommands(JsonObject commandsObj) {
        if (commandsObj == null) {
            System.out.println("No commands to execute");
            return;
        }
        
        if (!commandsObj.has("commands")) {
            System.out.println("Commands object does not have 'commands' property");
            return;
        }
        
        JsonArray commandsArray = commandsObj.getAsJsonArray("commands");
        if (commandsArray.size() == 0) {
            System.out.println("Commands array is empty, no changes to apply");
            return;
        }
        
        System.out.println("Executing " + commandsArray.size() + " plan edit commands:");
        
        for (int i = 0; i < commandsArray.size(); i++) {
            JsonElement commandElement = commandsArray.get(i);
            if (!commandElement.isJsonPrimitive()) {
                System.out.println("  - ERROR: Command is not a string: " + commandElement);
                continue;
            }
            
            String command = commandElement.getAsString();
            if (command.isEmpty()) {
                System.out.println("  - Empty command, skipping");
                continue;
            }
            
            System.out.println("Executing command [" + (i+1) + "/" + commandsArray.size() + "]: " + command);
            
            try {
                if (command.startsWith("getTask(")) {
                    // Parse the task ID part
                    int openParen = command.indexOf("(");
                    int closeParen = findMatchingClosingParenthesis(command, openParen);
                    if (closeParen == -1) {
                        System.out.println("  - ERROR: Cannot find matching closing parenthesis for task ID in: " + command);
                        continue;
                    }
                    
                    String idStr = command.substring(openParen + 1, closeParen).trim();
                    int id = Integer.parseInt(idStr);
                    String remainder = command.substring(closeParen + 1).trim();

                    TaskEditor task = getTask(id);
                    if (task == null) {
                        System.out.println("  - ERROR: Task " + id + " not found for command: " + command);
                        continue;
                    }

                    if (remainder.startsWith(".change(")) {
                        int methodOpenParen = remainder.indexOf("(");
                        int methodCloseParen = findMatchingClosingParenthesis(remainder, methodOpenParen);
                        
                        if (methodCloseParen == -1) {
                            System.out.println("  - ERROR: Cannot find matching closing parenthesis for change method in: " + remainder);
                            continue;
                        }
                        
                        String params = remainder.substring(methodOpenParen + 1, methodCloseParen);
                        
                        // Find the comma that separates property name from value
                        int commaPos = findPropertyValueSeparator(params);
                        if (commaPos == -1) {
                            System.out.println("  - ERROR: Cannot find property/value separator in: " + params);
                            continue;
                        }
                        
                        String propertyPart = params.substring(0, commaPos).trim();
                        String valuePart = params.substring(commaPos + 1).trim();
                        
                        // Extract property name from quotes
                        String property = removeQuotes(propertyPart);
                        
                        System.out.println("  - Changing property '" + property + "' of task " + id + " to: " + valuePart);
                        
                        try {
                            // Try to parse as JSON first
                            JsonElement jsonValue = JsonParser.parseString(valuePart);
                            task.change(property, jsonValue);
                            System.out.println("  - Applied JSON change to property: " + property);
                        } catch (JsonSyntaxException e) {
                            // If not valid JSON, use as string
                            String stringValue = removeQuotes(valuePart);
                            task.change(property, stringValue);
                            System.out.println("  - Applied string change to property: " + property);
                        }
                    } else if (remainder.startsWith(".appendCommand(")) {
                        int methodOpenParen = remainder.indexOf("(");
                        int methodCloseParen = findMatchingClosingParenthesis(remainder, methodOpenParen);
                        
                        if (methodCloseParen == -1) {
                            System.out.println("  - ERROR: Cannot find matching closing parenthesis for appendCommand method in: " + remainder);
                            continue;
                        }
                        
                        String param = remainder.substring(methodOpenParen + 1, methodCloseParen);
                        String cmdToAppend = removeQuotes(param);
                        System.out.println("  - Appending command to task " + id + ": " + cmdToAppend);
                        task.appendCommand(cmdToAppend);
                    } else {
                        System.out.println("  - Unknown getTask() command: " + command);
                    }
                } else if (command.startsWith("removeTask(")) {
                    int openParen = command.indexOf("(");
                    int closeParen = findMatchingClosingParenthesis(command, openParen);
                    
                    if (closeParen == -1) {
                        System.out.println("  - ERROR: Cannot find matching closing parenthesis in: " + command);
                        continue;
                    }
                    
                    String idStr = command.substring(openParen + 1, closeParen).trim();
                    int id = Integer.parseInt(idStr);
                    System.out.println("  - Removing task " + id);
                    removeTask(id);
                } else if (command.startsWith("addTask(")) {
                    int openParen = command.indexOf("(");
                    int closeParen = findMatchingClosingParenthesis(command, openParen);
                    
                    if (closeParen == -1) {
                        System.out.println("  - ERROR: Cannot find matching closing parenthesis in: " + command);
                        continue;
                    }
                    
                    String jsonPart = command.substring(openParen + 1, closeParen).trim();
                    // Convert the JSON string to a JsonObject
                    JsonObject taskJson = JsonParser.parseString(jsonPart).getAsJsonObject();
                    // Use Gson to convert the JsonObject to a Task instance.
                    Gson gson = new Gson();
                    Task newTask = gson.fromJson(taskJson, Task.class);
                    int newTaskId = newTask.getId();
                    System.out.println("  - Adding new task with ID " + newTaskId + ": " + newTask.getDescription());
                    addTask(newTask);
                } else if (command.startsWith("updateTask(")) {
                    int openParen = command.indexOf("(");
                    int closeParen = findMatchingClosingParenthesis(command, openParen);
                    
                    if (closeParen == -1) {
                        System.out.println("  - ERROR: Cannot find matching closing parenthesis in: " + command);
                        continue;
                    }
                    
                    String params = command.substring(openParen + 1, closeParen).trim();
                    
                    // Find the comma that separates ID from JSON
                    int commaPos = findFirstUnbalancedComma(params);
                    if (commaPos == -1) {
                        System.out.println("  - ERROR: Cannot find separator between ID and properties in: " + params);
                        continue;
                    }
                    
                    String idStr = params.substring(0, commaPos).trim();
                    String jsonPart = params.substring(commaPos + 1).trim();
                    
                    int id = Integer.parseInt(idStr);
                    JsonObject updates = JsonParser.parseString(jsonPart).getAsJsonObject();
                    System.out.println("  - Updating task " + id + " with " + updates.keySet().size() + " properties");
                    updateTask(id, updates);
                } else {
                    System.out.println("  - Unknown command: " + command);
                }
                
                System.out.println("  - Command executed successfully");
            } catch (Exception e) {
                System.out.println("  - Error processing command: " + command);
                e.printStackTrace();
            }
        }
        
        System.out.println("Plan editing completed");
    }
    
    /**
     * Find the matching closing parenthesis for an opening parenthesis at the given position.
     * Handles nested parentheses correctly.
     */
    private int findMatchingClosingParenthesis(String text, int openPosition) {
        int nestLevel = 0;
        for (int i = openPosition; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                nestLevel++;
            } else if (c == ')') {
                nestLevel--;
                if (nestLevel == 0) {
                    return i;
                }
            }
        }
        return -1; // No matching closing parenthesis found
    }
    
    /**
     * Find the first comma that's not inside parentheses, brackets, or quotes.
     * Used to find the separator between the property name and value in a change command.
     */
    private int findPropertyValueSeparator(String text) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int nestParenLevel = 0;
        int nestBracketLevel = 0;
        int nestBraceLevel = 0;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            // Handle quotes (accounting for escapes)
            if (c == '\'' && (i == 0 || text.charAt(i-1) != '\\')) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && (i == 0 || text.charAt(i-1) != '\\')) {
                inDoubleQuote = !inDoubleQuote;
            }
            
            // Only track nesting when not in quotes
            if (!inSingleQuote && !inDoubleQuote) {
                if (c == '(') nestParenLevel++;
                else if (c == ')') nestParenLevel--;
                else if (c == '[') nestBracketLevel++;
                else if (c == ']') nestBracketLevel--;
                else if (c == '{') nestBraceLevel++;
                else if (c == '}') nestBraceLevel--;
                
                // Found an unbalanced comma
                if (c == ',' && nestParenLevel == 0 && nestBracketLevel == 0 && nestBraceLevel == 0) {
                    return i;
                }
            }
        }
        
        return -1; // No property/value separator found
    }
    
    /**
     * Find the first comma that's not inside nested structures.
     * Used for parsing updateTask parameters.
     */
    private int findFirstUnbalancedComma(String text) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int nestLevel = 0;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            // Handle quotes (accounting for escapes)
            if (c == '\'' && (i == 0 || text.charAt(i-1) != '\\')) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && (i == 0 || text.charAt(i-1) != '\\')) {
                inDoubleQuote = !inDoubleQuote;
            }
            
            // Only track nesting when not in quotes
            if (!inSingleQuote && !inDoubleQuote) {
                if (c == '(' || c == '[' || c == '{') nestLevel++;
                else if (c == ')' || c == ']' || c == '}') nestLevel--;
                
                // Found an unbalanced comma
                if (c == ',' && nestLevel == 0) {
                    return i;
                }
            }
        }
        
        return -1; // No unbalanced comma found
    }

    /**
     * Helper method to remove surrounding quotes from a string.
     */
    private String removeQuotes(String s) {
        s = s.trim();
        if ((s.startsWith("\"") && s.endsWith("\"")) || 
            (s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }

    /**
     * Inner class that wraps a Task and provides editing methods.
     */
    public static class TaskEditor {
        private Task task;

        public TaskEditor(Task task) {
            this.task = task;
        }

        /**
         * Updates a property of the task based on a JSON element.
         * Supported properties: description, successCriteria, commands, isAtomic, id.
         */
        public void change(String property, JsonElement value) {
            property = property.trim();
            
            if ("description".equals(property)) {
                task.setDescription(value.getAsString());
            } else if ("successCriteria".equals(property)) {
                task.setSuccessCriteria(value.getAsString());
            } else if ("commands".equals(property)) {
                if (value.isJsonArray()) {
                    List<String> cmds = new ArrayList<>();
                    JsonArray arr = value.getAsJsonArray();
                    for (JsonElement el : arr) {
                        cmds.add(el.getAsString());
                    }
                    task.setCommands(cmds);
                }
            } else if ("isAtomic".equals(property)) {
                task.setAtomic(value.getAsBoolean());
            } else if ("id".equals(property)) {
                task.setId(value.getAsInt());
            } else {
                System.out.println("Unknown property: " + property);
            }
        }

        /**
         * Overloaded change method to update a property using a String value.
         */
        public void change(String property, String value) {
            property = property.trim();
            
            if ("description".equals(property)) {
                task.setDescription(value);
            } else if ("successCriteria".equals(property)) {
                task.setSuccessCriteria(value);
            } else if ("commands".equals(property)) {
                // If a string is provided, assume it's a single command to add.
                List<String> cmds = task.getCommands();
                if (cmds == null) {
                    cmds = new ArrayList<>();
                }
                cmds.add(value);
                task.setCommands(cmds);
            } else if ("isAtomic".equals(property)) {
                task.setAtomic(Boolean.parseBoolean(value));
            } else if ("id".equals(property)) {
                task.setId(Integer.parseInt(value));
            } else {
                System.out.println("Unknown property: " + property);
            }
        }

        /**
         * Appends a new command to the task's command list.
         */
        public void appendCommand(String command) {
            List<String> cmds = task.getCommands();
            if (cmds == null) {
                cmds = new ArrayList<>();
            }
            cmds.add(command);
            task.setCommands(cmds);
        }

        /**
         * Removes a command from the task's command list.
         */
        public void removeCommand(String command) {
            List<String> cmds = task.getCommands();
            if (cmds != null) {
                cmds.remove(command);
                task.setCommands(cmds);
            }
        }
    }
}