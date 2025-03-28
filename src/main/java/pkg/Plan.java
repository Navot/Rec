package pkg;

import java.util.ArrayList;
import java.util.List;

public class Plan {
    private List<Task> topLevelTasks;

    public Plan() {
        this.topLevelTasks = new ArrayList<>();
    }

    public List<Task> getTopLevelTasks() {
        return topLevelTasks;
    }

    public void addTask(Task task) {
        topLevelTasks.add(task);
    }
}