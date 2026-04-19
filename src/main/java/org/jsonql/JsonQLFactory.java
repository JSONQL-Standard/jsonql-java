package org.jsonql;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.jsonql.schema.JsonQLSchema;

/**
 * Factory helpers for creating JSONQL drivers and loading schemas.
 *
 * <p>Matches the Go SDK's factory.go and Python SDK's factory.py for cross-language consistency.
 *
 * <h3>Usage:</h3>
 *
 * <pre>
 * // Load schema
 * JsonQLSchema schema = JsonQLFactory.mustLoadSchema("schema.json");
 *
 * // Create a JDBC driver from environment
 * JsonQLDriver driver = JsonQLFactory.createDriver("postgres");
 *
 * // Create with explicit DSN
 * JsonQLDriver driver = JsonQLFactory.createDriverWithDSN("sqlite", "jdbc:sqlite:test.db");
 *
 * // Environment helper
 * String port = JsonQLFactory.envOr("PORT", "8080");
 * </pre>
 */
public final class JsonQLFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonQLFactory() {}

    // ── Environment helpers ────────────────────────────────────────────

    /**
     * Return the value of an environment variable, or a fallback if unset or empty.
     *
     * @param key environment variable name
     * @param fallback default value
     * @return the environment value or fallback
     */
    public static String envOr(String key, String fallback) {
        String val = System.getenv(key);
        if (val == null || val.isEmpty()) {
            return fallback;
        }
        return val;
    }

    // ── Schema loading ─────────────────────────────────────────────────

    /**
     * Load a JSONQL schema from a JSON file.
     *
     * @param path file path to a schema JSON file
     * @return the loaded schema, or null on error
     */
    @SuppressWarnings("unchecked")
    public static JsonQLSchema loadSchema(String path) {
        try {
            Map<String, Object> raw = MAPPER.readValue(new File(path), Map.class);
            return new JsonQLSchema(raw);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Load a JSONQL schema from a JSON file, throwing on failure.
     *
     * @param path file path to a schema JSON file
     * @return the loaded schema
     * @throws RuntimeException if the file cannot be read or parsed
     */
    @SuppressWarnings("unchecked")
    public static JsonQLSchema mustLoadSchema(String path) {
        try {
            Map<String, Object> raw = MAPPER.readValue(new File(path), Map.class);
            return new JsonQLSchema(raw);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to load schema from " + path + ": " + e.getMessage(), e);
        }
    }

    // ── JDBC Driver creation ───────────────────────────────────────────

    /**
     * Create a JDBC-based JSONQL driver using environment variables for DSN.
     *
     * <p>Reads {@code DB_DSN} for postgres/mysql/mssql and {@code DB_FILENAME} for sqlite. The DSN
     * should be a JDBC URL (e.g. {@code jdbc:postgresql://host/db}).
     *
     * @param dialect one of "sqlite", "postgres", "mysql", "mssql"
     * @return a JsonQLDriver wrapping a JDBC connection
     * @throws RuntimeException if the connection cannot be established
     */
    public static JsonQLDriver createDriver(String dialect) {
        String dsn;
        switch (dialect.toLowerCase()) {
            case "sqlite":
                String filename = envOr("DB_FILENAME", envOr("DB_DSN", ":memory:"));
                dsn = filename.startsWith("jdbc:") ? filename : "jdbc:sqlite:" + filename;
                break;
            case "postgres":
            case "postgresql":
                dsn = envOr("DB_DSN", "jdbc:postgresql://localhost:5432/jsonql");
                if (!dsn.startsWith("jdbc:")) dsn = "jdbc:postgresql:" + dsn;
                break;
            case "mysql":
                dsn = envOr("DB_DSN", "jdbc:mysql://localhost:3306/jsonql");
                if (!dsn.startsWith("jdbc:")) dsn = "jdbc:mysql:" + dsn;
                break;
            case "mssql":
            case "sqlserver":
                dsn = envOr("DB_DSN", "jdbc:sqlserver://localhost:1433;databaseName=jsonql");
                if (!dsn.startsWith("jdbc:")) dsn = "jdbc:sqlserver:" + dsn;
                break;
            default:
                throw new IllegalArgumentException("Unknown dialect: " + dialect);
        }
        return createDriverWithDSN(dialect, dsn);
    }

    /**
     * Create a JDBC-based JSONQL driver with an explicit DSN.
     *
     * @param dialect the SQL dialect name
     * @param dsn a JDBC connection URL
     * @return a JsonQLDriver wrapping a JDBC connection
     * @throws RuntimeException if the connection cannot be established
     */
    public static JsonQLDriver createDriverWithDSN(String dialect, String dsn) {
        try {
            Connection conn = DriverManager.getConnection(dsn);
            return new JdbcDriver(dialect, dsn, conn);
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to connect to " + dialect + " at " + dsn + ": " + e.getMessage(), e);
        }
    }

    // ── Internal JDBC driver implementation ────────────────────────────

    private static class JdbcDriver implements JsonQLDriver {
        private final String dialectName;
        private final String dsn;
        private Connection connection;

        JdbcDriver(String dialect, String dsn, Connection connection) {
            this.dialectName = dialect.toLowerCase();
            this.dsn = dsn;
            this.connection = connection;
        }

        @Override
        public List<Map<String, Object>> query(String sql, List<Object> parameters)
                throws SQLException {
            ensureConnection();
            try (var stmt = connection.prepareStatement(sql)) {
                for (int i = 0; i < parameters.size(); i++) {
                    stmt.setObject(i + 1, parameters.get(i));
                }
                try (var rs = stmt.executeQuery()) {
                    var hydrator = new org.jsonql.hydrator.ResultHydrator();
                    return hydrator.hydrate(rs);
                }
            }
        }

        @Override
        public int execute(String sql, List<Object> parameters) throws SQLException {
            ensureConnection();
            try (var stmt = connection.prepareStatement(sql)) {
                for (int i = 0; i < parameters.size(); i++) {
                    stmt.setObject(i + 1, parameters.get(i));
                }
                return stmt.executeUpdate();
            }
        }

        @Override
        public String dialect() {
            return dialectName;
        }

        @Override
        public Connection getConnection() {
            try {
                ensureConnection();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get connection: " + e.getMessage(), e);
            }
            return connection;
        }

        @Override
        public void close() throws Exception {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        }

        private void ensureConnection() throws SQLException {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(dsn);
            }
        }
    }
}
