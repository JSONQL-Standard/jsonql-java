package org.jsonql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Database driver abstraction for JSONQL.
 *
 * <p>Implementations manage a database connection and execute SQL queries/commands. This mirrors
 * the Go SDK's {@code Driver} interface for cross-SDK consistency.
 *
 * <pre>
 * JsonQLDriver driver = new SqliteDriver("./my.db");
 * List&lt;Map&lt;String, Object&gt;&gt; rows = driver.query(sql, params);
 * </pre>
 */
public interface JsonQLDriver extends AutoCloseable {

    /**
     * Execute a SELECT query and return the result rows.
     *
     * @param sql the SQL query string
     * @param parameters bind parameters
     * @return list of rows, each row is a column-name → value map
     * @throws SQLException if the query fails
     */
    List<Map<String, Object>> query(String sql, List<Object> parameters) throws SQLException;

    /**
     * Execute a non-SELECT SQL statement (INSERT, UPDATE, DELETE).
     *
     * @param sql the SQL statement
     * @param parameters bind parameters
     * @return the number of rows affected
     * @throws SQLException if the execution fails
     */
    int execute(String sql, List<Object> parameters) throws SQLException;

    /** Return the SQL dialect name (e.g. "sqlite", "postgres", "mysql"). */
    String dialect();

    /** Return the underlying JDBC connection. Useful for advanced scenarios or transactions. */
    Connection getConnection();
}
