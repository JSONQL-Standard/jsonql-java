package org.jsonql;

import org.jsonql.cache.CacheProvider;
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
import java.util.Set;

public class JsonQLEngine {

    private final SQLTranspiler transpiler;
    private final ResultHydrator hydrator;
    private final org.jsonql.schema.JsonQLSchema schema;
    private final JsonQLLogger logger;
    private final CacheProvider cache;
    private final int cacheTtl;

    protected JsonQLEngine() {
        this.transpiler = null;
        this.hydrator = null;
        this.schema = null;
        this.logger = JsonQLLogger.NoOpLogger.INSTANCE;
        this.cache = null;
        this.cacheTtl = 0;
    }

    public JsonQLEngine(SQLTranspiler transpiler) {
        this(transpiler, null);
    }

    public JsonQLEngine(SQLTranspiler transpiler, org.jsonql.schema.JsonQLSchema schema) {
        this(transpiler, schema, null);
    }

    public JsonQLEngine(SQLTranspiler transpiler, org.jsonql.schema.JsonQLSchema schema, JsonQLLogger logger) {
        this(transpiler, schema, logger, null, 60);
    }

    public JsonQLEngine(SQLTranspiler transpiler, org.jsonql.schema.JsonQLSchema schema,
                        JsonQLLogger logger, CacheProvider cache, int cacheTtl) {
        this.transpiler = transpiler;
        this.hydrator = new ResultHydrator();
        this.schema = schema;
        this.logger = logger != null ? logger : JsonQLLogger.NoOpLogger.INSTANCE;
        this.cache = cache;
        this.cacheTtl = cacheTtl;
    }

    /** Quote an identifier (table/column name) using the engine's dialect rules. */
    public String quoteIdentifier(String identifier) {
        if (transpiler != null && transpiler.getDialect() != null) {
            return transpiler.getDialect().quoteIdentifier(identifier);
        }
        return "\"" + identifier + "\"";
    }

    private static final Set<String> ALLOWED_QUERY_KEYS = Set.of(
        "version", "from", "where", "sort", "limit", "skip", "offset",
        "fields", "include", "groupBy", "distinct", "aggregate",
        "op", "data", "patch", "insert", "delete"
    );

    public List<Map<String, Object>> execute(Connection conn, String defaultTable, Map<String, Object> query, JsonQLLifecycle lifecycle) throws SQLException {
        // Validation: Version check
        if (query.containsKey("version")) {
            Object v = query.get("version");
            if (!"1.0".equals(v) && !"1.1".equals(v)) {
                throw new IllegalArgumentException("Invalid JSONQL Query");
            }
        }

        // Validation: Unknown keys
        for (String key : query.keySet()) {
            if (!ALLOWED_QUERY_KEYS.contains(key)) {
                throw new IllegalArgumentException("Unknown property \"" + key + "\" in query");
            }
        }

        // Validation: Negative limit
        if (query.containsKey("limit")) {
            Object l = query.get("limit");
            if (l instanceof Number && ((Number) l).intValue() < 0) {
                throw new IllegalArgumentException("limit must be a non-negative number");
            }
        }

        // Validation: Negative skip
        if (query.containsKey("skip")) {
            Object s = query.get("skip");
            if (s instanceof Number && ((Number) s).intValue() < 0) {
                throw new IllegalArgumentException("skip must be a non-negative number");
            }
        }

        // Validation: Negative offset
        if (query.containsKey("offset")) {
            Object o = query.get("offset");
            if (o instanceof Number && ((Number) o).intValue() < 0) {
                throw new IllegalArgumentException("offset must be a non-negative number");
            }
        }

        // Validation: Sort type
        if (query.containsKey("sort")) {
            Object sort = query.get("sort");
            if (!(sort instanceof String) && !(sort instanceof Map) && !(sort instanceof List)) {
                throw new IllegalArgumentException("sort must be a string, object, or array");
            }
        }

        // Validation: Unknown WHERE operators
        if (query.containsKey("where")) {
            validateWhereOperators(query.get("where"));
        }

        // Validation: groupBy field names (prevent SQL injection)
        if (query.containsKey("groupBy")) {
            Object gb = query.get("groupBy");
            if (gb instanceof List) {
                for (Object field : (List<?>) gb) {
                    if (field instanceof String && !isValidIdentifier((String) field)) {
                        throw new IllegalArgumentException("Invalid groupBy field name: " + field);
                    }
                }
            }
        }

        // Validation: aggregate field names (prevent SQL injection)
        if (query.containsKey("aggregate")) {
            Object agg = query.get("aggregate");
            if (agg instanceof Map) {
                for (Object val : ((Map<?, ?>) agg).values()) {
                    if (val instanceof Map) {
                        for (Object fieldRef : ((Map<?, ?>) val).values()) {
                            if (fieldRef instanceof String && !isValidIdentifier((String) fieldRef)) {
                                throw new IllegalArgumentException("Invalid aggregate field name: " + fieldRef);
                            }
                        }
                    }
                }
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

        // 4. Determine mutation sub-type and fire before-mutation hooks
        String mutationOp = null;
        if (isMutation) {
            if (query.containsKey("delete") || (query.containsKey("where") && !query.containsKey("data") && !query.containsKey("patch"))) {
                mutationOp = "delete";
            } else if (query.containsKey("patch") || (query.containsKey("where") && query.containsKey("data"))) {
                mutationOp = "update";
            } else {
                mutationOp = "create";
            }
            if (lifecycle != null) {
                switch (mutationOp) {
                    case "create": query = lifecycle.beforeCreate(query); break;
                    case "update": query = lifecycle.beforeUpdate(query); break;
                    case "delete": query = lifecycle.beforeDelete(query); break;
                }
            }
        }

        // 5. Transpile (with optional cache)
        String cacheKey = null;
        SQLTranspiler.TranspilationResult result = null;

        if (!isMutation && cache != null) {
            cacheKey = "transpile:" + table + ":" + query.hashCode();
            Object cached = cache.get(cacheKey);
            if (cached instanceof SQLTranspiler.TranspilationResult) {
                result = (SQLTranspiler.TranspilationResult) cached;
                logger.debug("Cache hit for %s", cacheKey);
            }
        }

        if (result == null) {
            if (!isMutation) {
                result = transpiler.transpile(query, table, schema);
            } else {
                switch (mutationOp) {
                    case "delete": {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> where = (Map<String, Object>) query.get("where");
                        result = transpiler.transpileDelete(where, table);
                        break;
                    }
                    case "update": {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> patchData = (Map<String, Object>) (query.containsKey("patch") ? query.get("patch") : query.get("data"));
                        @SuppressWarnings("unchecked")
                        Map<String, Object> where = (Map<String, Object>) query.get("where");
                        result = transpiler.transpileUpdate(patchData, where, table);
                        break;
                    }
                    default: {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> insertDataMap2 = (Map<String, Object>) query.get("data");
                        result = transpiler.transpileInsert(insertDataMap2, table);
                        break;
                    }
                }
            }
            // Cache SELECT transpilation results
            if (!isMutation && cache != null && cacheKey != null) {
                cache.set(cacheKey, result, cacheTtl);
            }
        }

        // 6. Lifecycle: afterTranspile
        if (lifecycle != null) {
            lifecycle.afterTranspile(result.sql, result.parameters);
        }

        // 7. Lifecycle: beforeExecute
        logger.debug("SQL: %s", result.sql);
        if (lifecycle != null) {
            lifecycle.beforeExecute(result.sql, result.parameters);
        }

        // 6. Execute
        List<Map<String, Object>> data;
        boolean isNonReturningDialect = !transpiler.getDialect().supportsReturning();
        boolean isInsertMutation = isMutation && result.sql.toUpperCase().trim().startsWith("INSERT");
        boolean isMssql = "mssql".equals(transpiler.getDialect().getName());

        // For MSSQL: wrap INSERT with IDENTITY_INSERT ON/OFF only when data contains an explicit PK
        String pk = "id";
        if (schema != null && schema.tables.containsKey(table)) {
            pk = schema.tables.get(table).primaryKey;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> insertData = isMutation && query.containsKey("data") ? (Map<String, Object>) query.get("data") : null;
        boolean needsIdentityInsert = isInsertMutation && isMssql && insertData != null && insertData.containsKey(pk);
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

        // Lifecycle: afterExecute
        if (lifecycle != null) {
            lifecycle.afterExecute(data);
        }

        // Lifecycle: beforeHydrate / afterHydrate (SELECT only)
        if (!isMutation && lifecycle != null) {
            data = lifecycle.beforeHydrate(data);
        }
        // Hydration is already done inline above; beforeHydrate allows pre-filtering.
        if (!isMutation && lifecycle != null) {
            data = lifecycle.afterHydrate(data);
        }

        // Lifecycle: after-mutation hooks
        if (isMutation && lifecycle != null && mutationOp != null) {
            switch (mutationOp) {
                case "create": lifecycle.afterCreate(query, data); break;
                case "update": lifecycle.afterUpdate(query, data); break;
                case "delete": lifecycle.afterDelete(query, data); break;
            }
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

            // Fallback: use explicit PK from the INSERT data
            if (insertedId == null) {
                Map<String, Object> insertDataMap = (Map<String, Object>) query.get("data");
                if (insertDataMap != null) {
                    String followUpPk = "id";
                    if (schema != null && schema.tables.containsKey(table)) {
                        followUpPk = schema.tables.get(table).primaryKey;
                    }
                    insertedId = insertDataMap.get(followUpPk);
                }
            }

            if (insertedId != null) {
                String selectPk = "id";
                if (schema != null && schema.tables.containsKey(table)) {
                    selectPk = schema.tables.get(table).primaryKey;
                }
                return executeFollowUpSelect(conn, table, Map.of(selectPk, insertedId));
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
        private CacheProvider cache;
        private int cacheTtl = 60;

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

        /** Enable query transpilation caching with the given provider. */
        public Builder cache(CacheProvider cache) {
            this.cache = cache;
            return this;
        }

        /** Set cache TTL in seconds (default 60). */
        public Builder cacheTtl(int seconds) {
            this.cacheTtl = seconds;
            return this;
        }

        public JsonQLEngine build() {
            if (dialect == null) dialect = new PostgresDialect();
            return new JsonQLEngine(new SQLTranspiler(dialect), schema, logger, cache, cacheTtl);
        }
    }

    private static final Set<String> VALID_WHERE_OPERATORS = Set.of(
        "eq", "ne", "neq", "gt", "gte", "lt", "lte",
        "like", "ilike", "in", "nin", "between",
        "is", "not", "contains", "startsWith", "endsWith",
        "starts", "ends"
    );

    private void validateWhereOperators(Object where) {
        if (!(where instanceof Map)) return;
        Map<?, ?> whereMap = (Map<?, ?>) where;
        for (Map.Entry<?, ?> entry : whereMap.entrySet()) {
            String key = entry.getKey().toString();
            // Skip logical operators and field names
            if ("or".equals(key) || "and".equals(key) || "not".equals(key)) {
                Object val = entry.getValue();
                if (val instanceof List) {
                    for (Object item : (List<?>) val) {
                        validateWhereOperators(item);
                    }
                } else {
                    validateWhereOperators(val);
                }
                continue;
            }
            // This is a field name — check its operator map
            Object val = entry.getValue();
            if (val instanceof Map) {
                Map<?, ?> ops = (Map<?, ?>) val;
                for (Object opKey : ops.keySet()) {
                    String op = opKey.toString();
                    if (!VALID_WHERE_OPERATORS.contains(op)) {
                        throw new IllegalArgumentException("Unknown operator \"" + op + "\" in where clause");
                    }
                }
            }
        }
    }

    private static boolean isValidIdentifier(String id) {
        if (id == null || id.isEmpty()) return false;
        String[] segments = id.split("\\.", -1);
        for (String seg : segments) {
            if (seg.isEmpty()) return false;
            char first = seg.charAt(0);
            if (!Character.isLetter(first) && first != '_') return false;
            for (int i = 1; i < seg.length(); i++) {
                char c = seg.charAt(i);
                if (!Character.isLetterOrDigit(c) && c != '_') return false;
            }
        }
        return true;
    }
}
