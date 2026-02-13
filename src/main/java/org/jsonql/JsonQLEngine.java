package org.jsonql;

import org.jsonql.dialect.*;
import org.jsonql.hydrator.ResultHydrator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonQLEngine {

    private final SQLTranspiler transpiler;
    private final ResultHydrator hydrator;
    private final org.jsonql.schema.JsonQLSchema schema;
    private final JsonQLLogger logger;

    protected JsonQLEngine() {
        this.transpiler = null;
        this.hydrator = null;
        this.schema = null;
        this.logger = JsonQLLogger.NoOpLogger.INSTANCE;
    }

    public JsonQLEngine(SQLTranspiler transpiler) {
        this(transpiler, null);
    }

    public JsonQLEngine(SQLTranspiler transpiler, org.jsonql.schema.JsonQLSchema schema) {
        this(transpiler, schema, null);
    }

    public JsonQLEngine(SQLTranspiler transpiler, org.jsonql.schema.JsonQLSchema schema, JsonQLLogger logger) {
        this.transpiler = transpiler;
        this.hydrator = new ResultHydrator();
        this.schema = schema;
        this.logger = logger != null ? logger : JsonQLLogger.NoOpLogger.INSTANCE;
    }

    /** Quote an identifier (table/column name) using the engine's dialect rules. */
    public String quoteIdentifier(String identifier) {
        if (transpiler != null && transpiler.getDialect() != null) {
            return transpiler.getDialect().quoteIdentifier(identifier);
        }
        return "\"" + identifier + "\"";
    }

    public List<Map<String, Object>> execute(Connection conn, String defaultTable, Map<String, Object> query, JsonQLLifecycle lifecycle) throws SQLException {
        // Validation: Version check
        if (query.containsKey("version")) {
            Object v = query.get("version");
            if (!"1.0".equals(v) && !"1.1".equals(v)) {
                throw new IllegalArgumentException("Invalid JSONQL Query");
            }
        }

        // 1. Determine Table
        String table = defaultTable;
        if (query.containsKey("from") && query.get("from") instanceof String) {
            table = (String) query.get("from");
        }
        if (table == null) {
            throw new IllegalArgumentException("Table name not specified in query or context");
        }

        // 2. Determine Operation Type
        boolean isMutation = query.containsKey("data") || query.containsKey("patch") || query.containsKey("insert") || 
                            (query.containsKey("delete") && !query.containsKey("fields")); 
                            // Basic heuristic, can be improved
        
        String commandType = isMutation ? "MUTATION" : "SELECT";
        logger.debug("Executing %s on table %s", commandType, table);

        // 3. Lifecycle: beforeTranspile
        if (lifecycle != null) {
            lifecycle.beforeTranspile(query, commandType);
        }

        // 4. Transpile
        SQLTranspiler.TranspilationResult result;
        if (!isMutation) {
            result = transpiler.transpile(query, table, schema);
        } else {
            // Mutation Logic
            if (query.containsKey("delete") || (query.containsKey("where") && !query.containsKey("data") && !query.containsKey("patch"))) {
                // DELETE
                @SuppressWarnings("unchecked")
                Map<String, Object> where = (Map<String, Object>) query.get("where");
                result = transpiler.transpileDelete(where, table);
            } else if (query.containsKey("patch") || (query.containsKey("where") && query.containsKey("data"))) {
                // UPDATE
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) (query.containsKey("patch") ? query.get("patch") : query.get("data"));
                @SuppressWarnings("unchecked")
                Map<String, Object> where = (Map<String, Object>) query.get("where");
                result = transpiler.transpileUpdate(data, where, table);
            } else {
                // INSERT
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) query.get("data");
                result = transpiler.transpileInsert(data, table);
            }
        }

        // 5. Lifecycle: beforeExecute
        logger.debug("SQL: %s", result.sql);
        if (lifecycle != null) {
            lifecycle.beforeExecute(result.sql, result.parameters);
        }

        // 6. Execute
        List<Map<String, Object>> data;
        boolean isNonReturningDialect = !(transpiler.getDialect() instanceof org.jsonql.dialect.PostgresDialect);
        boolean isInsertMutation = isMutation && result.sql.toUpperCase().trim().startsWith("INSERT");
        boolean isMssql = transpiler.getDialect() instanceof org.jsonql.dialect.MSSQLDialect;

        // For MSSQL: wrap INSERT with IDENTITY_INSERT ON/OFF only when data contains an explicit "id"
        @SuppressWarnings("unchecked")
        Map<String, Object> insertData = isMutation && query.containsKey("data") ? (Map<String, Object>) query.get("data") : null;
        boolean needsIdentityInsert = isInsertMutation && isMssql && insertData != null && insertData.containsKey("id");
        if (needsIdentityInsert) {
            try {
                conn.createStatement().execute("SET IDENTITY_INSERT [" + table + "] ON");
            } catch (Exception ignored) { /* table may not have identity column */ }
        }

        try (PreparedStatement stmt = isInsertMutation && isNonReturningDialect
                ? conn.prepareStatement(result.sql, Statement.RETURN_GENERATED_KEYS)
                : conn.prepareStatement(result.sql)) {
            for (int i = 0; i < result.parameters.size(); i++) {
                stmt.setObject(i + 1, result.parameters.get(i));
            }

            boolean hasResultSet = stmt.execute();
            if (hasResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    data = hydrator.hydrate(rs);
                }
            } else if (isMutation && isNonReturningDialect) {
                // MySQL/SQLite: no RETURNING clause, do a follow-up SELECT
                data = fetchMutationResult(conn, stmt, query, table, isInsertMutation);
            } else {
                data = Collections.emptyList();
            }
        }

        // Turn off IDENTITY_INSERT after execution
        if (needsIdentityInsert) {
            try {
                conn.createStatement().execute("SET IDENTITY_INSERT [" + table + "] OFF");
            } catch (Exception ignored) { }
        }

        // 7. Lifecycle: afterExecute
        if (lifecycle != null) {
            lifecycle.afterExecute(data);
        }

        return data;
    }

    // ── Follow-up SELECT for non-RETURNING dialects (MySQL/SQLite) ─────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchMutationResult(
            Connection conn, PreparedStatement mutationStmt,
            Map<String, Object> query, String table, boolean isInsert) throws SQLException {

        if (isInsert) {
            // INSERT: find the inserted row by generated key or explicit id
            Object insertedId = null;

            // Try JDBC getGeneratedKeys()
            try (ResultSet gk = mutationStmt.getGeneratedKeys()) {
                if (gk.next()) {
                    Object key = gk.getObject(1);
                    if (key instanceof Number && ((Number) key).longValue() > 0) {
                        insertedId = key;
                    }
                }
            }

            // Fallback: use explicit id from the INSERT data
            if (insertedId == null) {
                Map<String, Object> insertData = (Map<String, Object>) query.get("data");
                if (insertData != null) {
                    insertedId = insertData.get("id");
                }
            }

            if (insertedId != null) {
                return executeFollowUpSelect(conn, table, Map.of("id", insertedId));
            }
            return Collections.emptyList();

        } else if (query.containsKey("patch") || query.containsKey("where")) {
            // UPDATE: re-select using the original WHERE clause
            Map<String, Object> where = (Map<String, Object>) query.get("where");
            if (where != null && !where.isEmpty()) {
                return executeFollowUpSelect(conn, table, where);
            }
        }

        // DELETE or no WHERE: return empty list
        return Collections.emptyList();
    }

    private List<Map<String, Object>> executeFollowUpSelect(
            Connection conn, String table, Map<String, Object> where) throws SQLException {

        Map<String, Object> selectQuery = new HashMap<>();
        selectQuery.put("where", where);
        SQLTranspiler.TranspilationResult selectResult = transpiler.transpile(selectQuery, table, schema);

        try (PreparedStatement ps = conn.prepareStatement(selectResult.sql)) {
            for (int i = 0; i < selectResult.parameters.size(); i++) {
                ps.setObject(i + 1, selectResult.parameters.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                return hydrator.hydrate(rs);
            }
        }
    }

    // ── Convenience API ─────────────────────────────────────────────────

    /**
     * Execute a {@link JsonQLRequestNormalizer.NormalizedRequest} with lifecycle hooks.
     *
     * @param conn       JDBC connection
     * @param request    normalized request (from {@link JsonQLRequestNormalizer#normalize})
     * @param lifecycle  lifecycle hooks (may be null)
     * @return a {@link JsonQLResult} wrapping the data and operation metadata
     */
    public JsonQLResult executeRequest(Connection conn, JsonQLRequestNormalizer.NormalizedRequest request, JsonQLLifecycle lifecycle) throws SQLException {
        String table = request.getTable();
        if (table == null) {
            throw new IllegalArgumentException("Table name required");
        }
        List<Map<String, Object>> results = execute(conn, table, request.getQuery(), lifecycle);
        return new JsonQLResult(results, request.isMutation());
    }

    /**
     * Execute a {@link JsonQLRequestNormalizer.NormalizedRequest} without lifecycle hooks.
     */
    public JsonQLResult executeRequest(Connection conn, JsonQLRequestNormalizer.NormalizedRequest request) throws SQLException {
        return executeRequest(conn, request, null);
    }

    // ── Factory methods ─────────────────────────────────────────────────

    /**
     * Create an engine for the given SQL dialect and schema.
     *
     * @param dialectType one of "postgres", "mysql", "sqlite"
     * @param schema      optional schema for validation and relationships
     * @return a configured engine
     */
    public static JsonQLEngine create(String dialectType, org.jsonql.schema.JsonQLSchema schema) {
        return builder().dialect(dialectType).schema(schema).build();
    }

    /** Start building a new engine. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link JsonQLEngine}.
     * <pre>
     * JsonQLEngine engine = JsonQLEngine.builder()
     *     .postgres()
     *     .schema(schema)
     *     .build();
     * </pre>
     */
    public static class Builder {
        private SQLDialect dialect;
        private org.jsonql.schema.JsonQLSchema schema;
        private JsonQLLogger logger;

        public Builder postgres()  { this.dialect = new PostgresDialect(); return this; }
        public Builder mysql()     { this.dialect = new MySQLDialect();    return this; }
        public Builder sqlite()    { this.dialect = new SQLiteDialect();   return this; }

        public Builder dialect(SQLDialect dialect) {
            this.dialect = dialect;
            return this;
        }

        public Builder dialect(String type) {
            switch (type.toLowerCase()) {
                case "postgres": case "postgresql": return postgres();
                case "mysql":    return mysql();
                case "sqlite":   return sqlite();
                case "mssql": case "sqlserver": this.dialect = new MSSQLDialect(); return this;
                default: this.dialect = new GenericDialect(); return this;
            }
        }

        public Builder schema(org.jsonql.schema.JsonQLSchema schema) {
            this.schema = schema;
            return this;
        }

        public Builder logger(JsonQLLogger logger) {
            this.logger = logger;
            return this;
        }

        public JsonQLEngine build() {
            if (dialect == null) dialect = new PostgresDialect();
            return new JsonQLEngine(new SQLTranspiler(dialect), schema, logger);
        }
    }
}
