package org.jsonql;

import java.util.*;

/**
 * Wraps JSONQL execution results with metadata for HTTP response construction.
 *
 * <p>Usage:
 *
 * <pre>
 * JsonQLResult result = engine.executeRequest(conn, request);
 * Map&lt;String, Object&gt; body = result.toResponseBody(); // {"data": [...]}
 * boolean wasMutation = result.isMutation();
 * </pre>
 */
public class JsonQLResult {

    private final List<Map<String, Object>> data;
    private final boolean mutation;

    public JsonQLResult(List<Map<String, Object>> data, boolean mutation) {
        this.data = data != null ? data : Collections.emptyList();
        this.mutation = mutation;
    }

    /** The result rows. */
    public List<Map<String, Object>> getData() {
        return data;
    }

    /** Whether this result came from a mutation (INSERT, UPDATE, DELETE). */
    public boolean isMutation() {
        return mutation;
    }

    /** Whether this result came from a query (SELECT). */
    public boolean isQuery() {
        return !mutation;
    }

    /** Build the standard JSONQL response body: {@code { "data": [...] }} */
    public Map<String, Object> toResponseBody() {
        Map<String, Object> response = new HashMap<>();
        response.put("data", data);
        return response;
    }
}
