package org.jsonql;

/**
 * Base exception for all JSONQL errors.
 *
 * <p>Every JSONQL exception carries a machine-readable {@link #getCode() code} string (e.g. {@code
 * "VALIDATION_ERROR"}, {@code "TRANSPILE_ERROR"}) that mirrors the error codes used in the
 * TypeScript, Python, and Go SDKs.
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

    private final String code;

    public JsonQLException(String message) {
        this(message, "JSONQL_ERROR");
    }

    public JsonQLException(String message, String code) {
        super(message);
        this.code = code;
    }

    public JsonQLException(String message, Throwable cause) {
        this(message, "JSONQL_ERROR", cause);
    }

    public JsonQLException(String message, String code, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** Machine-readable error code, e.g. {@code "VALIDATION_ERROR"}. */
    public String getCode() {
        return code;
    }
}
