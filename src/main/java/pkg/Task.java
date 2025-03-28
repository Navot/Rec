package pkg;

import java.util.ArrayList;
import java.util.List;

public class Task {
    private boolean isAtomic;
    private List<Task> subTasks = new ArrayList<>();
    private int id;
    private String description;
    private List<String>  commands;
    private String successCriteria;
    private boolean completed = false;
    private boolean inProgress = false;


    public Task(boolean isAtomic, int id, String description, List<String>  commands, String successCriteria) {
        this.isAtomic = isAtomic;
        this.id = id;
        this.description = description;
        this.commands = commands;
        this.successCriteria = successCriteria;
        this.completed = false;
        this.inProgress = false;
    }

    public int getId() { return id; }

    public void setId(int id) { this.id = id; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getCommands() { return commands; }
    public void setCommands(List<String> commands) { this.commands = commands; }

    public String getSuccessCriteria() { return successCriteria; }
    public void setSuccessCriteria(String successCriteria) { this.successCriteria = successCriteria; }

    public boolean isAtomic() { return isAtomic; }
    public void setAtomic(boolean isAtomic) { this.isAtomic = isAtomic; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public boolean isInProgress() { return inProgress; }
    public void setInProgress(boolean inProgress) { this.inProgress = inProgress; }

    public void addSubTask(Task subTask) {
        subTasks.add(subTask);
    }

    public List<Task> getSubTasks() {
        return subTasks;
    }

    public void setSubTasks(List<Task> subTasks) {
        this.subTasks = subTasks;
    }

    public void initSubTasks() {
        this.subTasks = new ArrayList<>();
    }
}
