package pkg.web;

import org.springframework.stereotype.Service;

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
} 