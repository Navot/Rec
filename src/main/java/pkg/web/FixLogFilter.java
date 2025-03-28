package pkg.web;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Helper class to filter logs related to plan fixes
 */
public class FixLogFilter {
    
    private final Predicate<LogEntry> fixLogPredicate = entry -> 
        entry.getMessage().contains("Plan is invalid") ||
        entry.getMessage().contains("fixing") ||
        entry.getMessage().contains("fix the plan") ||
        entry.getMessage().contains("Applied plan fixes") ||
        entry.getMessage().contains("validation") ||
        entry.getMessage().contains("Evaluating plan") ||
        entry.getMessage().contains("commands") ||
        entry.getMessage().toLowerCase().contains("plan");
    
    /**
     * Filter the provided logs to only include fix-related entries
     * 
     * @param logs The full list of logs to filter
     * @return A filtered list containing only plan fix related logs
     */
    public List<LogEntry> filterFixLogs(List<LogEntry> logs) {
        return logs.stream()
                .filter(fixLogPredicate)
                .collect(Collectors.toList());
    }
    
    /**
     * Get the predicate used for filtering fix logs
     * 
     * @return The predicate that determines if a log entry is fix-related
     */
    public Predicate<LogEntry> getFixLogPredicate() {
        return fixLogPredicate;
    }
} 