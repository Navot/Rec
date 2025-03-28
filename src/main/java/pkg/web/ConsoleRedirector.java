package pkg.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.PrintStream;

/**
 * Redirects standard console output to our logging service
 */
@Component
public class ConsoleRedirector {

    private final LogService logService;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private LoggingOutputStream loggingOut;
    private LoggingOutputStream loggingErr;

    @Autowired
    public ConsoleRedirector(LogService logService) {
        this.logService = logService;
    }

    @PostConstruct
    public void init() {
        // Save original streams
        originalOut = System.out;
        originalErr = System.err;

        // Create logging streams
        loggingOut = new LoggingOutputStream(logService, LogEntry.LogLevel.INFO);
        loggingErr = new LoggingOutputStream(logService, LogEntry.LogLevel.ERROR);

        // Redirect standard output and error
        System.setOut(new PrintStream(loggingOut, true));
        System.setErr(new PrintStream(loggingErr, true));
    }

    @PreDestroy
    public void cleanup() {
        // Restore original streams
        System.setOut(originalOut);
        System.setErr(originalErr);
        
        try {
            if (loggingOut != null) loggingOut.close();
            if (loggingErr != null) loggingErr.close();
        } catch (Exception e) {
            originalErr.println("Error closing logging streams: " + e.getMessage());
        }
    }
} 