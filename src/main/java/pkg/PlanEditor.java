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
    public void executeCommands(JsonObject fixCommands) {
        if (!fixCommands.has("commands") || !fixCommands.get("commands").isJsonArray()) {
            System.out.println("No commands found.");
            return;
        }
        JsonArray commandsArray = fixCommands.getAsJsonArray("commands");
        for (JsonElement element : commandsArray) {
            String command = element.getAsString().trim();
            try {
                if (command.startsWith("getTask(")) {
                    int openParen = command.indexOf("(");
                    int closeParen = command.indexOf(")", openParen);
                    String idStr = command.substring(openParen + 1, closeParen).trim();
                    int id = Integer.parseInt(idStr);
                    String remainder = command.substring(closeParen + 1).trim();
                    if (remainder.startsWith(".change(")) {
                        int start = remainder.indexOf("(") + 1;
                        int end = remainder.lastIndexOf(");");
                        String params = remainder.substring(start, end);
                        String[] parts = params.split(",\\s*");
                        if (parts.length == 2) {
                            String property = removeQuotes(parts[0]);
                            String value = removeQuotes(parts[1]);
                            TaskEditor task = getTask(id);
                            if (task != null) {
                                task.change(property, value);
                            }
                        }
                    } else if (remainder.startsWith(".appendCommand(")) {
                        int start = remainder.indexOf("(") + 1;
                        int end = remainder.lastIndexOf(");");
                        String param = remainder.substring(start, end);
                        String cmdToAppend = removeQuotes(param);
                        TaskEditor task = getTask(id);
                        if (task != null) {
                            task.appendCommand(cmdToAppend);
                        }
                    } else {
                        System.out.println("Unknown getTask() command: " + command);
                    }
                } else if (command.startsWith("removeTask(")) {
                    int openParen = command.indexOf("(");
                    int closeParen = command.indexOf(")", openParen);
                    String idStr = command.substring(openParen + 1, closeParen).trim();
                    int id = Integer.parseInt(idStr);
                    removeTask(id);
                } else if (command.startsWith("addTask(")) {
                    int openParen = command.indexOf("(");
                    int closeParen = command.lastIndexOf(");");
                    String jsonPart = command.substring(openParen + 1, closeParen).trim();
                    // Convert the JSON string to a JsonObject
                    JsonObject taskJson = JsonParser.parseString(jsonPart).getAsJsonObject();
                    // Use Gson to convert the JsonObject to a Task instance.
                    Gson gson = new Gson();
                    Task newTask = gson.fromJson(taskJson, Task.class);
                    addTask(newTask);
                } else if (command.startsWith("updateTask(")) {
                    int openParen = command.indexOf("(");
                    int comma = command.indexOf(",", openParen);
                    String idStr = command.substring(openParen + 1, comma).trim();
                    int id = Integer.parseInt(idStr);
                    int jsonStart = command.indexOf("{", comma);
                    int jsonEnd = command.lastIndexOf(");");
                    String jsonPart = command.substring(jsonStart, jsonEnd).trim();
                    JsonObject updates = JsonParser.parseString(jsonPart).getAsJsonObject();
                    updateTask(id, updates);
                } else {
                    System.out.println("Unknown command: " + command);
                }
            } catch (Exception e) {
                System.out.println("Error processing command: " + command);
                e.printStackTrace();
            }
        }
    }

    /**
     * Helper method to remove surrounding double quotes from a string.
     */
    private String removeQuotes(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) {
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