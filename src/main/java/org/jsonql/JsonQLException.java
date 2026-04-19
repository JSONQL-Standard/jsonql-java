package org.jsonql;

/**
 * Base exception for all JSONQL errors.
 *
 * <p>Subclasses provide more specific context:
 *
 * <ul>
 *   <li>{@link JsonQLValidationException} — schema/query validation failures
 *   <li>{@link JsonQLTranspileException} — SQL generation failures
 *   <li>{@link JsonQLExecutionException} — runtime / database failures
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
