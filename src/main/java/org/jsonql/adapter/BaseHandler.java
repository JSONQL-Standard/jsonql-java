package org.jsonql.adapter;

import org.jsonql.*;
import org.jsonql.dialect.*;
import org.jsonql.hydrator.ResultHydrator;
import org.jsonql.schema.JsonQLSchema;
import org.jsonql.validator.JsonQLValidator;

import java.sql.Connection;
import java.util.*;

/**
 * Framework-agnostic base handler that implements the full JSONQL pipeline:
 * parse → validate → transpile → execute → hydrate.
 *
 * <p>Framework-specific adapters (Spring Boot, Jakarta EE, etc.) should
 * delegate to this handler for the core pipeline logic.</p>
 */
public class BaseHandler {

    private final AdapterOptions options;
    private final JsonQLParser parser;
    private final SQLTranspiler transpiler;
    private final ResultHydrator hydrator;
    private final JsonQLEngine engine;
    private final JsonQLLogger logger;

    public BaseHandler(AdapterOptions options) {
        this.options = options;
        this.parser = new JsonQLParser();
        this.transpiler = new SQLTranspiler(newDialect(options.dialect));
        this.hydrator = new ResultHydrator();
        this.engine = new JsonQLEngine(transpiler, options.schema, options.logger, options.cache, options.cacheTtl);

        if (options.logger != null) {
            this.logger = options.logger;
        } else if (options.debug) {
            this.logger = new JsonQLLogger.ConsoleLogger();
        } else {
            this.logger = JsonQLLogger.NoOpLogger.INSTANCE;
        }
    }

    /**
     * Process a JSONQL request and return the response data.
     *
     * @param rawInput   The raw JSON body as a Map
     * @param httpMethod The HTTP method (GET, POST, PUT, PATCH, DELETE)
     * @param pathName   URL path segment identifying the table (e.g., "users")
     * @return A result map with "data" and "meta" keys
     * @throws JsonQLException on parse/validation/transpile errors
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> processRequest(
            Map<String, Object> rawInput,
            String httpMethod,
            String pathName) throws Exception {

        Map<String, Object> rawQuery = rawInput != null ? new HashMap<>(rawInput) : new HashMap<>();

        // 1. Infer mutation op from HTTP method
        inferMutation(httpMethod, rawQuery);

        // 2. Parse (validate structure)
        parser.parse(rawQuery);

        // 3. Resolve table name
        String tableName = resolveTableName(rawQuery, pathName);

        // 4. Validate against schema
        if (options.schema != null && tableName != null) {
            var tableSchema = options.schema.tables.get(tableName);
            if (tableSchema != null && !tableSchema.fields.isEmpty()) {
                JsonQLValidator validator = new JsonQLValidator(options.schema, tableName);
                var validation = validator.validate(rawQuery);
                if (!validation.valid) {
                    throw new JsonQLValidationException("Validation Error", validation.errors);
                }
            }
        }

        // 5. Execute via engine
        if (options.connectionSupplier != null) {
            Connection conn = options.connectionSupplier.get();
            try {
                List<Map<String, Object>> data = engine.execute(
                        conn, tableName, rawQuery, options.lifecycle);
                Map<String, Object> result = new HashMap<>();
                result.put("meta", Map.of("query", rawQuery));
                result.put("data", data);
                return result;
            } finally {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }

        // No connection supplier — return parsed query only
        Map<String, Object> result = new HashMap<>();
        result.put("meta", Map.of("query", rawQuery));
        result.put("data", Collections.emptyList());
        return result;
    }

    /**
     * Resolve the target table from query body and URL path.
     */
    private String resolveTableName(Map<String, Object> query, String pathName) {
        String tableName = (String) query.get("from");

        if (options.tableMapping != null) {
            String mapped = options.tableMapping.get(pathName);
            if (mapped != null) {
                if (tableName != null) {
                    throw new IllegalArgumentException(
                            "Cannot specify 'from' on a mapped endpoint. This endpoint is mapped to: " + mapped);
                }
                return mapped;
            }
        }

        if (options.allowedTables != null) {
            if (tableName == null) tableName = pathName;
            if (!options.allowedTables.contains(tableName)) {
                throw new IllegalArgumentException("Table '" + tableName + "' is not allowed");
            }
            return tableName;
        }

        // Open mode
        if (tableName == null || tableName.isEmpty()) {
            tableName = pathName;
        }
        return tableName;
    }

    /**
     * Infer mutation operation from HTTP method.
     */
    private void inferMutation(String httpMethod, Map<String, Object> raw) {
        if (raw.containsKey("op")) return;

        if (raw.containsKey("create")) {
            raw.put("op", "create");
            if (!raw.containsKey("data")) {
                raw.put("data", raw.get("create"));
            }
            return;
        }
        if (raw.containsKey("update")) {
            raw.put("op", "update");
            if (!raw.containsKey("patch")) {
                raw.put("patch", raw.get("update"));
            }
            return;
        }
        if (raw.containsKey("delete")) {
            raw.put("op", "delete");
            return;
        }

        Object upsertRaw = raw.get("upsert");
        if (upsertRaw instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> upsert = (Map<String, Object>) upsertRaw;
            if (upsert.containsKey("where") && upsert.containsKey("update")) {
                raw.put("op", "update");
                raw.put("where", upsert.get("where"));
                raw.put("patch", upsert.get("update"));
                return;
            }
            if (upsert.containsKey("create")) {
                raw.put("op", "create");
                raw.put("data", upsert.get("create"));
                return;
            }
        }

        if (httpMethod == null) return;
        switch (httpMethod.toUpperCase()) {
            case "POST":
                if (raw.containsKey("data")) {
                    raw.put("op", "create");
                }
                break;
            case "PUT":
            case "PATCH":
                if (raw.containsKey("patch") || raw.containsKey("where")) {
                    raw.put("op", "update");
                }
                break;
            case "DELETE":
                if (raw.containsKey("where")) {
                    raw.put("op", "delete");
                }
                break;
        }
    }

    /**
     * Create a SQL dialect from a dialect name string.
     */
    private static SQLDialect newDialect(String name) {
        if (name == null) return new SQLiteDialect();
        switch (name.toLowerCase()) {
            case "postgres":
            case "postgresql":
                return new PostgresDialect();
            case "mysql":
                return new MySQLDialect();
            case "mssql":
            case "sqlserver":
                return new MSSQLDialect();
            case "sqlite":
            default:
                return new SQLiteDialect();
        }
    }
}
