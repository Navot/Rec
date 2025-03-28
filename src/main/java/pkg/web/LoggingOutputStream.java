package pkg.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Custom output stream that redirects console output to our logging service
 */
public class LoggingOutputStream extends OutputStream {
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);
    private final LogService logService;
    private final LogEntry.LogLevel level;

    public LoggingOutputStream(LogService logService, LogEntry.LogLevel level) {
        this.logService = logService;
        this.level = level;
    }

    @Override
    public void write(int b) throws IOException {
        if (b == '\n') {
            // On newline, log the current buffer and reset
            String line = buffer.toString().trim();
            if (!line.isEmpty()) {
                logMessage(line);
            }
            buffer.reset();
        } else {
            buffer.write(b);
        }
    }

    @Override
    public void flush() throws IOException {
        // Log any remaining content in the buffer
        String remaining = buffer.toString().trim();
        if (!remaining.isEmpty()) {
            logMessage(remaining);
            buffer.reset();
        }
    }

    @Override
    public void close() throws IOException {
        flush();
        buffer.close();
    }

    private void logMessage(String message) {
        switch (level) {
            case INFO:
                logService.addInfo(message);
                break;
            case ERROR:
                logService.addError(message);
                break;
            case WARNING:
                logService.addWarning(message);
                break;
            case SUCCESS:
                logService.addSuccess(message);
                break;
            case COMMAND:
                // We don't expect to get command level logs from stdout/stderr
                logService.addInfo(message);
                break;
        }
    }
} 