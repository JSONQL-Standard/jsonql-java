package org.jsonql.adapter;

import java.util.*;
import org.jsonql.*;

/**
 * MongoDB adapter for JSONQL.
 *
 * <p>Uses {@link MongoTranspiler} to convert JSONQL queries into {@link MongoResult} descriptors,
 * then dispatches them to a {@link MongoDriverInterface} for execution.
 *
 * <p>Unlike the SQL-based {@link BaseHandler}, MongoDB results are already document-shaped so no
 * hydration step is needed.
 *
 * <h3>Usage:</h3>
 *
 * <pre>
 * MongoDriverInterface driver = new MyMongoDriver("mongodb://localhost:27017", "mydb");
 * MongoAdapterOptions opts = new MongoAdapterOptions()
 *         .driver(driver)
 *         .logger(new JsonQLLogger.ConsoleLogger());
 *
 * MongoAdapter adapter = new MongoAdapter(opts);
 * Map&lt;String, Object&gt; result = adapter.processRequest(body, "GET", "users");
 * </pre>
 */
public class MongoAdapter {

    private final MongoAdapterOptions options;
    private final JsonQLParser parser;
    private final MongoTranspiler transpiler;
    private final JsonQLLogger logger;

    public MongoAdapter(MongoAdapterOptions opts) {
        this.options = opts;
        this.parser = new JsonQLParser();
        this.transpiler = new MongoTranspiler();

        if (opts.logger != null) {
            this.logger = opts.logger;
        } else if (opts.debug) {
            this.logger = new JsonQLLogger.ConsoleLogger();
        } else {
            this.logger = JsonQLLogger.NoOpLogger.INSTANCE;
        }
    }

    /**
     * Process a JSONQL request against MongoDB.
     *
     * @param rawInput parsed request body
     * @param httpMethod HTTP method string
     * @param pathName URL path segment (collection name)
     * @return result map with "meta" and "data" keys
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> processRequest(
            Map<String, Object> rawInput, String httpMethod, String pathName) throws Exception {

        Map<String, Object> rawQuery = rawInput != null ? new HashMap<>(rawInput) : new HashMap<>();

        // 1. Infer mutation from HTTP method
        inferMutation(httpMethod, rawQuery);

        // 2. Parse
        parser.parse(rawQuery);

        // 3. Determine collection name
        String collection = (String) rawQuery.getOrDefault("from", pathName);
        if (collection == null || collection.isEmpty()) {
            collection = pathName;
        }

        logger.debug("[JSONQL] MongoDB request: %s on %s", httpMethod, collection);

        // 4. Check for mutation
        String op = (String) rawQuery.get("op");
        if (op != null) {
            return handleMutation(op, rawQuery, collection);
        }

        // 5. Transpile query → MongoResult
        MongoResult result = transpiler.transpile(rawQuery, collection);
        logger.debug("[JSONQL] MongoDB op=%s collection=%s", result.operation, result.collection);

        // 6. Execute
        List<Map<String, Object>> data;
        if ("aggregate".equals(result.operation)) {
            data = options.driver.executeAggregate(result);
        } else {
            data = options.driver.executeFind(result);
        }

        // No hydration needed — MongoDB returns documents directly
        Map<String, Object> response = new HashMap<>();
        response.put("meta", Map.of("query", rawInput));
        response.put("data", data != null ? data : Collections.emptyList());
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleMutation(
            String op, Map<String, Object> rawQuery, String collection) throws Exception {

        Object resultData;

        switch (op.toLowerCase()) {
            case "create":
                {
                    Map<String, Object> data = (Map<String, Object>) rawQuery.get("data");
                    if (data == null) {
                        throw new JsonQLException("Missing 'data' for create operation");
                    }
                    MongoResult result = transpiler.transpileInsert(data, collection);
                    resultData = options.driver.executeInsert(result);
                    break;
                }
            case "update":
                {
                    Map<String, Object> patch = (Map<String, Object>) rawQuery.get("patch");
                    if (patch == null) {
                        throw new JsonQLException("Missing 'patch' for update operation");
                    }
                    Map<String, Object> where = (Map<String, Object>) rawQuery.get("where");
                    MongoResult result = transpiler.transpileUpdate(patch, where, collection);
                    long count = options.driver.executeUpdate(result);
                    resultData = Map.of("modifiedCount", count);
                    break;
                }
            case "delete":
                {
                    Map<String, Object> where = (Map<String, Object>) rawQuery.get("where");
                    MongoResult result = transpiler.transpileDelete(where, collection);
                    long count = options.driver.executeDelete(result);
                    resultData = Map.of("deletedCount", count);
                    break;
                }
            default:
                throw new JsonQLException("Unknown operation: " + op);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("meta", Map.of("query", rawQuery));
        response.put("data", resultData);
        return response;
    }

    private void inferMutation(String httpMethod, Map<String, Object> raw) {
        if (raw.containsKey("op")) return;
        if (httpMethod == null) return;
        switch (httpMethod.toUpperCase()) {
            case "POST":
                raw.put("op", "create");
                break;
            case "PUT":
            case "PATCH":
                raw.put("op", "update");
                break;
            case "DELETE":
                raw.put("op", "delete");
                break;
        }
    }
}
