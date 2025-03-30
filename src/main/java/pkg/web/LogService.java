package pkg.web;

import org.springframework.stereotype.Service;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Service for managing log entries
 */
@Service
public class LogService {
    private final CopyOnWriteArrayList<LogEntry> logs = new CopyOnWriteArrayList<>();
    private final AtomicLong idCounter = new AtomicLong(0);

    /**
     * Add a new info log entry
     */
    public LogEntry addInfo(String message) {
        return addLog(message, LogEntry.LogLevel.INFO);
    }

    /**
     * Add a new error log entry
     */
    public LogEntry addError(String message) {
        return addLog(message, LogEntry.LogLevel.ERROR);
    }

    /**
     * Add a new success log entry
     */
    public LogEntry addSuccess(String message) {
        return addLog(message, LogEntry.LogLevel.SUCCESS);
    }

    /**
     * Add a new command log entry
     */
    public LogEntry addCommand(String command) {
        return addLog("Executing command: " + command, LogEntry.LogLevel.COMMAND);
    }

    /**
     * Add a new warning log entry
     */
    public LogEntry addWarning(String message) {
        return addLog(message, LogEntry.LogLevel.WARNING);
    }

    /**
     * Add a new LLM request log entry
     */
    public LogEntry addLlmRequest(String message) {
        return addLog(message, LogEntry.LogLevel.LLM_REQUEST);
    }

    /**
     * Add a new LLM response log entry
     */
    public LogEntry addLlmResponse(String message) {
        return addLog(message, LogEntry.LogLevel.LLM_RESPONSE);
    }

    /**
     * Add a log entry with specified level
     */
    private LogEntry addLog(String message, LogEntry.LogLevel level) {
        LogEntry entry = new LogEntry(idCounter.incrementAndGet(), message, level);
        logs.add(entry);
        return entry;
    }

    /**
     * Get all log entries
     */
    public List<LogEntry> getAllLogs() {
        return new ArrayList<>(logs);
    }

    /**
     * Get log entries since the specified ID
     */
    public List<LogEntry> getLogsSince(Long lastId) {
        return logs.stream()
                .filter(log -> log.getId() > lastId)
                .collect(Collectors.toList());
    }

    /**
     * Clear all logs
     */
    public void clearLogs() {
        logs.clear();
    }

    /**
     * Log raw JSON without timestamps
     * This will add a regular log entry with a message indicating JSON is being printed,
     * but will bypass the normal logging for the actual JSON content
     */
    public void addRawJson(String jsonContent) {
        // First add a normal log entry to indicate we're logging JSON
        addInfo("Printing JSON (without timestamps):");
        
        // Then get the original output stream and print directly to it
        PrintStream originalOut = null;
        try {
            ConsoleRedirector redirector = ConsoleRedirector.getInstance();
            if (redirector != null) {
                originalOut = redirector.getOriginalOut();
            }
        } catch (Exception e) {
            // Ignore any errors
        }
        
        if (originalOut != null) {
            // Print directly to original System.out
            originalOut.println("\n--- JSON OUTPUT START ---");
            originalOut.println(jsonContent);
            originalOut.println("--- JSON OUTPUT END ---\n");
        } else {
            // Fall back to regular logging if original stream is not available
            addInfo("Unable to print raw JSON, using regular logging instead:");
            addInfo(jsonContent);
        }
    }

    /**
     * Get all conversation logs (only LLM_REQUEST and LLM_RESPONSE)
     */
    public List<LogEntry> getConversationLogs() {
        return logs.stream()
                .filter(log -> log.getLevel() == LogEntry.LogLevel.LLM_REQUEST 
                        || log.getLevel() == LogEntry.LogLevel.LLM_RESPONSE)
                .collect(Collectors.toList());
    }
    
    /**
     * Get conversation logs since the specified ID
     */
    public List<LogEntry> getConversationLogsSince(Long lastId) {
        return logs.stream()
                .filter(log -> log.getId() > lastId)
                .filter(log -> log.getLevel() == LogEntry.LogLevel.LLM_REQUEST 
                        || log.getLevel() == LogEntry.LogLevel.LLM_RESPONSE)
                .collect(Collectors.toList());
    }
    
    /**
     * Get system logs (excluding LLM conversation logs)
     */
    public List<LogEntry> getSystemLogs() {
        return logs.stream()
                .filter(log -> log.getLevel() != LogEntry.LogLevel.LLM_REQUEST 
                        && log.getLevel() != LogEntry.LogLevel.LLM_RESPONSE)
                .collect(Collectors.toList());
    }
    
    /**
     * Get system logs since the specified ID
     */
    public List<LogEntry> getSystemLogsSince(Long lastId) {
        return logs.stream()
                .filter(log -> log.getId() > lastId)
                .filter(log -> log.getLevel() != LogEntry.LogLevel.LLM_REQUEST 
                        && log.getLevel() != LogEntry.LogLevel.LLM_RESPONSE)
                .collect(Collectors.toList());
    }
} 