package org.jsonql;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/**
 * Normalizes HTTP request components into a unified JSONQL query map.
 *
 * <p>This class handles the conversion from HTTP semantics (method, path, body, query params) to
 * the JSONQL query format expected by {@link JsonQLEngine#execute}.
 *
 * <p>Usage:
 *
 * <pre>
 * NormalizedRequest req = JsonQLRequestNormalizer.normalize("POST", "users", body, params);
 * JsonQLResult result = engine.executeRequest(conn, req);
 * </pre>
 */
public class JsonQLRequestNormalizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** JSONQL-reserved keywords — these are never treated as implicit WHERE conditions. */
    public static final List<String> KEYWORDS =
            List.of(
                    "from",
                    "fields",
                    "include",
                    "where",
                    "sort",
                    "limit",
                    "skip",
                    "offset",
                    "aggregate",
                    "groupBy",
                    "version",
                    "distinct",
                    "data",
                    "patch",
                    "delete",
                    "insert");

    /** Query-specific keys that indicate a POST body is a query, not a mutation. */
    private static final List<String> QUERY_KEYS =
            List.of(
                    "fields",
                    "where",
                    "aggregate",
                    "sort",
                    "include",
                    "groupBy",
                    "distinct",
                    "version",
                    "from",
                    "limit",
                    "skip",
                    "offset");

    /** The result of normalizing an HTTP request into a JSONQL query. */
    public static class NormalizedRequest {
        private final String table;
        private final Map<String, Object> query;
        private final boolean queryPayload;

        public NormalizedRequest(String table, Map<String, Object> query, boolean queryPayload) {
            this.table = table;
            this.query = query;
            this.queryPayload = queryPayload;
        }

        /** The resolved table name (from URL path or {@code "from"} key). May be null. */
        public String getTable() {
            return table;
        }

        /** The unified JSONQL query map, ready for {@link JsonQLEngine#execute}. */
        public Map<String, Object> getQuery() {
            return query;
        }

        /** Whether this request is a query (SELECT) rather than a mutation. */
        public boolean isQuery() {
            return queryPayload;
        }

        /** Whether this request is a mutation (INSERT/UPDATE/DELETE). */
        public boolean isMutation() {
            return !queryPayload;
        }
    }

    /**
     * Normalize HTTP request components into a unified JSONQL query.
     *
     * @param httpMethod HTTP method (GET, POST, PATCH, PUT, DELETE)
     * @param pathTable table name extracted from URL path (may be null)
     * @param body parsed JSON request body (may be null)
     * @param queryParams flat map of query string parameters (may be null)
     * @return a {@link NormalizedRequest} containing the resolved table, unified query, and
     *     operation type
     * @throws IllegalArgumentException if the {@code q} / {@code query} parameter contains invalid
     *     JSON
     */
    public static NormalizedRequest normalize(
            String httpMethod,
            String pathTable,
            Map<String, Object> body,
            Map<String, String> queryParams) {

        Map<String, Object> query = new HashMap<>();

        // 1. Merge request body
        if (body != null) query.putAll(body);

        // 2. Parse query string parameters
        if (queryParams != null) {
            parseQueryParams(queryParams, query);
        }

        // 3. Resolve table name
        String table = resolveTable(pathTable, query);

        // 4. Apply method-specific transformations
        boolean isQuery = applyMethodSemantics(httpMethod, query);

        return new NormalizedRequest(table, query, isQuery);
    }

    /**
     * Detect whether a request body represents a query (SELECT) vs a mutation.
     *
     * <p>A body is a query if it contains query-specific keys ({@code fields}, {@code where},
     * {@code aggregate}, etc.) and does not contain mutation keys ({@code data}, {@code insert},
     * {@code patch}, {@code delete}).
     *
     * @param body the parsed request body (may be null)
     * @return true if the body represents a query
     */
    public static boolean isQueryPayload(Map<String, Object> body) {
        if (body == null || body.isEmpty()) return false;
        if (body.containsKey("data")
                || body.containsKey("insert")
                || body.containsKey("patch")
                || body.containsKey("delete")) {
            return false;
        }
        for (String key : QUERY_KEYS) {
            if (body.containsKey(key)) return true;
        }
        return false;
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    private static void parseQueryParams(
            Map<String, String> queryParams, Map<String, Object> query) {
        // Handle JSON query string: ?query={...} or ?q={...}
        String jsonQuery = queryParams.get("query");
        if (jsonQuery == null) jsonQuery = queryParams.get("q");

        if (jsonQuery != null && !jsonQuery.isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = MAPPER.readValue(jsonQuery, Map.class);
                query.putAll(parsed);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON in query parameter");
            }
        }

        // Process remaining params: keywords go to query root, others become implicit WHERE
        Map<String, Object> implicitWhere = new HashMap<>();
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            String key = entry.getKey();
            if ("query".equals(key) || "q".equals(key)) continue;

            Object val = coerceValue(entry.getValue());
            if (KEYWORDS.contains(key)) {
                query.put(key, val);
            } else {
                implicitWhere.put(key, val);
            }
        }

        if (!implicitWhere.isEmpty()) {
            mergeWhere(query, implicitWhere);
        }
    }

    private static String resolveTable(String pathTable, Map<String, Object> query) {
        if (pathTable != null && !pathTable.isEmpty() && !"query".equals(pathTable)) {
            return pathTable;
        }
        if (query.containsKey("from")) {
            return (String) query.get("from");
        }
        return null;
    }

    private static boolean applyMethodSemantics(String httpMethod, Map<String, Object> query) {
        switch (httpMethod.toUpperCase()) {
            case "DELETE":
                query.put("delete", true);
                extractNonKeywordsTo(query, "where");
                return false;

            case "POST":
                if (isQueryPayload(query)) {
                    return true; // POST-as-query (search)
                }
                query.put("insert", true);
                if (!query.containsKey("data")) {
                    extractNonKeywordsTo(query, "data");
                }
                return false;

            case "PATCH":
            case "PUT":
                if (!query.containsKey("patch") && !query.containsKey("data")) {
                    extractNonKeywordsTo(query, "patch");
                }
                return false;

            default: // GET, HEAD, OPTIONS
                return true;
        }
    }

    @SuppressWarnings("unchecked")
    private static void mergeWhere(Map<String, Object> query, Map<String, Object> additional) {
        Map<String, Object> existing = (Map<String, Object>) query.get("where");
        if (existing == null) {
            query.put("where", additional);
        } else {
            Map<String, Object> merged = new HashMap<>(existing);
            merged.putAll(additional);
            query.put("where", merged);
        }
    }

    private static void extractNonKeywordsTo(Map<String, Object> query, String targetKey) {
        Map<String, Object> extracted = new HashMap<>();
        for (String key : new ArrayList<>(query.keySet())) {
            if (!KEYWORDS.contains(key)) {
                extracted.put(key, query.get(key));
                query.remove(key);
            }
        }
        if (!extracted.isEmpty()) {
            if ("where".equals(targetKey)) {
                mergeWhere(query, extracted);
            } else {
                query.put(targetKey, extracted);
            }
        }
    }

    private static Object coerceValue(String value) {
        if (value.matches("^-?\\d+$")) {
            try {
                return Integer.parseInt(value);
            } catch (Exception ignored) {
            }
        }
        return value;
    }
}
