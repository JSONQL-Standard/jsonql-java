package org.jsonql;

/**
 * Base exception for all JSONQL errors.
 *
 * Subclasses provide more specific context:
 * <ul>
 *   <li>{@link JsonQLValidationException} — schema/query validation failures</li>
 *   <li>{@link JsonQLTranspileException}  — SQL generation failures</li>
 *   <li>{@link JsonQLExecutionException}  — runtime / database failures</li>
 * </ul>
 */
public class JsonQLException extends RuntimeException {

    public JsonQLException(String message) {
        super(message);
    }

    public JsonQLException(String message, Throwable cause) {
        super(message, cause);
    }
}
