package org.jsonql;

import java.sql.SQLException;

/**
 * Thrown when a JSONQL query fails at the database execution stage.
 *
 * <p>Wraps the underlying {@link SQLException} while keeping a JSONQL-typed exception hierarchy so
 * callers can use a single catch block.
 */
public class JsonQLExecutionException extends JsonQLException {

    public JsonQLExecutionException(String message) {
        super(message);
    }

    public JsonQLExecutionException(String message, SQLException cause) {
        super(message, cause);
    }

    /** Return the underlying SQL exception, if any. */
    @Override
    public synchronized SQLException getCause() {
        return (SQLException) super.getCause();
    }
}
