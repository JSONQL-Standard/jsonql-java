package org.jsonql;

import java.util.HashMap;
import java.util.Map;

/**
 * Public HTTP/REST helper utilities for JSONQL adapters.
 *
 * <p>Provides the same helpers as Go's adapters/http package and Python's adapters.mongo_base
 * module:
 *
 * <ul>
 *   <li>{@link #inferMutation} — derive JSONQL op from HTTP method + body
 *   <li>{@link #getIdFromQuery} — extract ?id= from query parameters
 *   <li>{@link #buildRestMutation} — build a JSONQL mutation from REST verbs
 * </ul>
 */
public final class JsonQLHttpHelpers {

    private JsonQLHttpHelpers() {}

    /**
     * Infer the JSONQL mutation operation from HTTP method and request body.
     *
     * <p>Modifies the raw map in-place by setting "op", "data", "patch", "where" as needed.
     *
     * <p>Logic:
     *
     * <ul>
     *   <li>If "create", "update", or "delete" keys are already present, use them.
     *   <li>If "upsert" is present, extract accordingly.
     *   <li>POST + data → create; POST without data → query
     *   <li>PATCH/PUT → update
     *   <li>DELETE → delete
     * </ul>
     *
     * @param httpMethod HTTP method string (GET, POST, PUT, PATCH, DELETE)
     * @param raw the request body map (modified in-place)
     */
    @SuppressWarnings("unchecked")
    public static void inferMutation(String httpMethod, Map<String, Object> raw) {
        if (raw.containsKey("op")) return;

        // Explicit operation keys
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

        // Upsert
        Object upsertRaw = raw.get("upsert");
        if (upsertRaw instanceof Map<?, ?>) {
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

        // HTTP method inference
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
     * Extract an "id" from HTTP query parameters.
     *
     * <p>Attempts to parse as integer; if that fails, returns the raw string.
     *
     * @param queryParams map of query parameter key-value pairs
     * @return the id value (Integer or String), or null if not present
     */
    public static Object getIdFromQuery(Map<String, String> queryParams) {
        if (queryParams == null) return null;
        String idStr = queryParams.get("id");
        if (idStr == null || idStr.isEmpty()) return null;
        try {
            return Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            return idStr;
        }
    }

    /**
     * Build a JSONQL mutation map from REST-style HTTP verbs.
     *
     * <p>Converts RESTful conventions to JSONQL operations:
     *
     * <ul>
     *   <li>POST + body with "data" → create mutation
     *   <li>PATCH/PUT + body with "patch" + ?id= → update mutation
     *   <li>DELETE + ?id= → delete mutation
     *   <li>GET → returns null (queries handled separately)
     * </ul>
     *
     * @param method HTTP method
     * @param queryParams URL query parameters
     * @param body parsed request body
     * @return a JSONQL mutation map, or null for GET/query requests
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> buildRestMutation(
            String method, Map<String, String> queryParams, Map<String, Object> body) {

        if (method == null) return null;
        if (body == null) body = new HashMap<>();

        switch (method.toUpperCase()) {
            case "POST":
                {
                    if (body.containsKey("data")) {
                        Map<String, Object> mutation = new HashMap<>(body);
                        mutation.put("op", "create");
                        return mutation;
                    }
                    return null;
                }
            case "PUT":
            case "PATCH":
                {
                    Object id = getIdFromQuery(queryParams);
                    Map<String, Object> mutation = new HashMap<>(body);
                    mutation.put("op", "update");
                    if (id != null && !mutation.containsKey("where")) {
                        mutation.put("where", Map.of("id", Map.of("eq", id)));
                    }
                    return mutation;
                }
            case "DELETE":
                {
                    Object id = getIdFromQuery(queryParams);
                    Map<String, Object> mutation = new HashMap<>(body);
                    mutation.put("op", "delete");
                    if (id != null && !mutation.containsKey("where")) {
                        mutation.put("where", Map.of("id", Map.of("eq", id)));
                    }
                    return mutation;
                }
            default:
                return null;
        }
    }
}
