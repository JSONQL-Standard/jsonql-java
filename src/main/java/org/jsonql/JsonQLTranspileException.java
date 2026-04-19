package org.jsonql;

/**
 * Thrown when JSONQL-to-SQL transpilation fails.
 *
 * <p>Common causes: invalid field names, unsupported operators, missing schema for includes.
 */
public class JsonQLTranspileException extends JsonQLException {

    public JsonQLTranspileException(String message) {
        super(message, "TRANSPILE_ERROR");
    }

    public JsonQLTranspileException(String message, Throwable cause) {
        super(message, "TRANSPILE_ERROR", cause);
    }
}
