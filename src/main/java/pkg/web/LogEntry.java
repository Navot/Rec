package pkg.web;

import java.time.LocalDateTime;

/**
 * Represents a single log entry for the UI
 */
public class LogEntry {
    private Long id;
    private String message;
    private LocalDateTime timestamp;
    private LogLevel level;

    public enum LogLevel {
        INFO, WARNING, ERROR, SUCCESS, COMMAND, LLM_REQUEST, LLM_RESPONSE
    }
    
    public LogEntry() {
        // Default constructor for JSON deserialization
    }

    public LogEntry(Long id, String message, LogLevel level) {
        this.id = id;
        this.message = message;
        this.timestamp = LocalDateTime.now();
        this.level = level;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public LogLevel getLevel() {
        return level;
    }

    public void setLevel(LogLevel level) {
        this.level = level;
    }
} 