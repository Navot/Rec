package pkg.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class LogController {
    private final LogService logService;
    private final FixLogFilter fixLogFilter;

    @Autowired
    public LogController(LogService logService) {
        this.logService = logService;
        this.fixLogFilter = new FixLogFilter();
    }

    @GetMapping
    public List<LogEntry> getLogs(@RequestParam(required = false, defaultValue = "0") Long since) {
        if (since == 0) {
            return logService.getAllLogs();
        } else {
            return logService.getLogsSince(since);
        }
    }
    
    /**
     * Get log entries related to plan fixes and validation
     * This endpoint allows filtering logs that contain information about plan validation
     * and fixes, which can be helpful for understanding how the plan was modified
     */
    @GetMapping("/fixes")
    public List<LogEntry> getPlanFixLogs() {
        return fixLogFilter.filterFixLogs(logService.getAllLogs());
    }

    /**
     * Get only conversation logs (LLM requests and responses)
     */
    @GetMapping("/conversation")
    public List<LogEntry> getConversationLogs(@RequestParam(required = false, defaultValue = "0") Long since) {
        if (since == 0) {
            return logService.getConversationLogs();
        } else {
            return logService.getConversationLogsSince(since);
        }
    }

    /**
     * Get only system logs (excluding LLM conversation logs)
     */
    @GetMapping("/system")
    public List<LogEntry> getSystemLogs(@RequestParam(required = false, defaultValue = "0") Long since) {
        if (since == 0) {
            return logService.getSystemLogs();
        } else {
            return logService.getSystemLogsSince(since);
        }
    }
} 