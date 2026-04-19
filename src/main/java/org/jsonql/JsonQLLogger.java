package org.jsonql;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logger interface for JSONQL operations. Implement this interface to integrate with your preferred
 * logging framework.
 */
public interface JsonQLLogger {

    void debug(String message, Object... args);

    void info(String message, Object... args);

    void warn(String message, Object... args);

    void error(String message, Object... args);

    /** Console logger that writes to stdout/stderr. Useful for development and debugging. */
    class ConsoleLogger implements JsonQLLogger {
        private static final DateTimeFormatter FMT =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        private final Level level;

        public enum Level {
            DEBUG,
            INFO,
            WARN,
            ERROR,
            SILENT
        }

        public ConsoleLogger() {
            this(Level.INFO);
        }

        public ConsoleLogger(Level level) {
            this.level = level;
        }

        @Override
        public void debug(String message, Object... args) {
            if (level.ordinal() <= Level.DEBUG.ordinal()) {
                log("DEBUG", message, args);
            }
        }

        @Override
        public void info(String message, Object... args) {
            if (level.ordinal() <= Level.INFO.ordinal()) {
                log("INFO", message, args);
            }
        }

        @Override
        public void warn(String message, Object... args) {
            if (level.ordinal() <= Level.WARN.ordinal()) {
                log("WARN", message, args);
            }
        }

        @Override
        public void error(String message, Object... args) {
            if (level.ordinal() <= Level.ERROR.ordinal()) {
                log("ERROR", message, args);
            }
        }

        private void log(String level, String message, Object... args) {
            String timestamp = LocalDateTime.now().format(FMT);
            String formatted = args.length > 0 ? String.format(message, args) : message;
            System.out.printf("[jsonql] %s %s: %s%n", timestamp, level, formatted);
        }
    }

    /** No-op logger that discards all output. Used as the default when no logger is configured. */
    class NoOpLogger implements JsonQLLogger {
        public static final NoOpLogger INSTANCE = new NoOpLogger();

        @Override
        public void debug(String message, Object... args) {}

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Object... args) {}
    }
}
