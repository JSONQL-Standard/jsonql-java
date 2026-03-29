package org.jsonql;

import java.util.ArrayList;
import java.util.List;

/**
 * Thrown from lifecycle hooks to signal an HTTP-level error.
 *
 * Carries an HTTP status code (e.g. 400, 403) and an optional list of
 * detail strings so that adapters can produce a structured error response
 * without inventing their own exception type.
 *
 * <pre>
 *   throw new JsonQLHookException(403, "Access denied");
 *   throw new JsonQLHookException(400, "Validation failed", List.of("field 'x' unknown"));
 * </pre>
 */
public class JsonQLHookException extends JsonQLException {

    private final int status;
    private final List<String> errors;

    public JsonQLHookException(int status, String message) {
        super(message);
        this.status = status;
        this.errors = null;
    }

    public JsonQLHookException(int status, String message, List<String> errors) {
        super(message);
        this.status = status;
        this.errors = errors != null ? new ArrayList<>(errors) : null;
    }

    /** HTTP status code for the error response. */
    public int getStatus() {
        return status;
    }

    /** Optional detail strings (e.g. individual validation messages). */
    public List<String> getErrors() {
        return errors;
    }

    /** True when this exception carries a list of detail errors. */
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
}
