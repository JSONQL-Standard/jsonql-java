package org.jsonql;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jsonql.JsonQLValidationException;
import org.jsonql.schema.JsonQLSchema;
import org.jsonql.validator.JsonQLValidator;

public class JsonQLParser {

    private JsonQLSchema schema;
    private String tableName;
    private JsonQLParserOptions options;

    public JsonQLParser() {}

    public JsonQLParser(JsonQLSchema schema, String tableName) {
        this.schema = schema;
        this.tableName = tableName;
    }

    public JsonQLParser(JsonQLSchema schema, String tableName, JsonQLParserOptions options) {
        this.schema = schema;
        this.tableName = tableName;
        this.options = options;
    }

    public JsonQLParser(JsonQLParserOptions options) {
        this.options = options;
    }

    public void setOptions(JsonQLParserOptions options) {
        this.options = options;
    }

    public void parse(Map<String, Object> query) throws IllegalArgumentException {
        // 1. Basic Syntax Validation
        validateSyntax(query);

        // 2. Parser Options Enforcement
        if (options != null) {
            enforceOptions(query);
        }

        // 3. Schema & Permission Validation
        if (schema != null && tableName != null) {
            JsonQLValidator validator = new JsonQLValidator(schema, tableName);
            JsonQLValidator.ValidationResult result = validator.validate(query);
            if (!result.valid) {
                // For now, throw the first error
                throw new JsonQLValidationException(result.errors.get(0).message, result.errors);
            }
        }
    }

    /**
     * Parses and validates the raw query map, returning a typed {@link JsonQLQuery}.
     *
     * <p>This is the preferred method for pipeline use: it validates the input (syntax, options,
     * schema) and then converts it to a strongly-typed query object that can be passed to the
     * transpiler.
     *
     * @param query the raw query map (e.g. from a parsed JSON body)
     * @return a validated {@link JsonQLQuery} instance
     * @throws IllegalArgumentException on validation failure
     */
    public JsonQLQuery parseToQuery(Map<String, Object> query) throws IllegalArgumentException {
        parse(query);
        return JsonQLQuery.fromMap(query);
    }

    @SuppressWarnings("unchecked")
    private void enforceOptions(Map<String, Object> query) {
        // MaxLimit enforcement
        if (options.getMaxLimit() > 0 && query.containsKey("limit")) {
            Object limitObj = query.get("limit");
            int limit = (limitObj instanceof Number) ? ((Number) limitObj).intValue() : 0;
            if (limit > options.getMaxLimit()) {
                throw new IllegalArgumentException(
                        String.format(
                                "limit %d exceeds maximum allowed limit of %d",
                                limit, options.getMaxLimit()));
            }
        }

        // MaxNestingDepth enforcement
        if (options.getMaxNestingDepth() > 0 && query.containsKey("include")) {
            int depth = calculateIncludeDepth(query.get("include"));
            if (depth > options.getMaxNestingDepth()) {
                throw new IllegalArgumentException(
                        String.format(
                                "include nesting depth %d exceeds maximum allowed depth of %d",
                                depth, options.getMaxNestingDepth()));
            }
        }

        // AllowedFields enforcement
        if (options.getAllowedFields() != null
                && !options.getAllowedFields().isEmpty()
                && query.containsKey("fields")) {
            Set<String> allowed = new HashSet<>(options.getAllowedFields());
            List<?> fields = (List<?>) query.get("fields");
            for (Object f : fields) {
                if (!allowed.contains(f.toString())) {
                    throw new IllegalArgumentException(
                            String.format("field '%s' is not in the allowed fields list", f));
                }
            }
        }

        // AllowedIncludes enforcement
        if (options.getAllowedIncludes() != null
                && !options.getAllowedIncludes().isEmpty()
                && query.containsKey("include")) {
            Set<String> allowed = new HashSet<>(options.getAllowedIncludes());
            Object include = query.get("include");
            if (include instanceof Map) {
                for (Object key : ((Map<?, ?>) include).keySet()) {
                    if (!allowed.contains(key.toString())) {
                        throw new IllegalArgumentException(
                                String.format(
                                        "include '%s' is not in the allowed includes list", key));
                    }
                }
            } else if (include instanceof List) {
                for (Object item : (List<?>) include) {
                    if (!allowed.contains(item.toString())) {
                        throw new IllegalArgumentException(
                                String.format(
                                        "include '%s' is not in the allowed includes list", item));
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private int calculateIncludeDepth(Object include) {
        int maxDepth = 1;
        if (include instanceof Map) {
            Map<String, Object> includeMap = (Map<String, Object>) include;
            for (Object val : includeMap.values()) {
                if (val instanceof Map) {
                    Map<String, Object> subMap = (Map<String, Object>) val;
                    if (subMap.containsKey("include")) {
                        int depth = 1 + calculateIncludeDepth(subMap.get("include"));
                        if (depth > maxDepth) maxDepth = depth;
                    }
                }
            }
        }
        return maxDepth;
    }

    private static final Set<String> ALLOWED_QUERY_KEYS =
            Set.of(
                    "version",
                    "from",
                    "where",
                    "sort",
                    "limit",
                    "skip",
                    "offset",
                    "fields",
                    "include",
                    "groupBy",
                    "distinct",
                    "aggregate",
                    "op",
                    "data",
                    "patch",
                    "insert",
                    "update",
                    "delete",
                    "upsert",
                    "create");

    private void validateSyntax(Map<String, Object> query) {
        // Reject unknown keys
        for (String key : query.keySet()) {
            if (!ALLOWED_QUERY_KEYS.contains(key)) {
                throw new IllegalArgumentException("Unknown property \"" + key + "\" in query");
            }
        }

        if (query.containsKey("version")) {
            String vs = String.valueOf(query.get("version"));
            if (!"1".equals(vs) && !"1.0".equals(vs) && !"1.1".equals(vs)) {
                throw new IllegalArgumentException("Invalid JSONQL Query");
            }
        }

        if (query.containsKey("fields")) {
            Object fields = query.get("fields");
            if (fields instanceof List) {
                List<?> fieldsList = (List<?>) fields;
                if (fieldsList.isEmpty()) {
                    throw new IllegalArgumentException("Fields array cannot be empty");
                }
                for (Object f : fieldsList) {
                    if (!isValidIdentifier(f.toString())) {
                        throw new IllegalArgumentException("Invalid field name");
                    }
                }
            }
        }

        // Validate limit
        if (query.containsKey("limit")) {
            Object l = query.get("limit");
            if (l instanceof Number) {
                if (((Number) l).intValue() < 0) {
                    throw new IllegalArgumentException("limit must be a non-negative number");
                }
            }
        }

        // Validate skip
        if (query.containsKey("skip")) {
            Object s = query.get("skip");
            if (s instanceof Number) {
                if (((Number) s).intValue() < 0) {
                    throw new IllegalArgumentException("skip must be a non-negative number");
                }
            }
        }

        // Validate offset
        if (query.containsKey("offset")) {
            Object o = query.get("offset");
            if (o instanceof Number) {
                if (((Number) o).intValue() < 0) {
                    throw new IllegalArgumentException("offset must be a non-negative number");
                }
            }
        }

        if (query.containsKey("sort")) {
            Object sort = query.get("sort");
            if (sort instanceof String) {
                String s = (String) sort;
                String field = s.startsWith("-") ? s.substring(1) : s;
                if (!isValidIdentifier(field)) {
                    throw new IllegalArgumentException("Invalid sort field");
                }
            } else if (sort instanceof List) {
                for (Object s : (List<?>) sort) {
                    String name = s.toString();
                    if (name.startsWith("-")) name = name.substring(1);
                    if (!isValidIdentifier(name)) {
                        throw new IllegalArgumentException("Invalid sort field: " + name);
                    }
                }
            }
        }

        // Validate include relation names
        if (query.containsKey("include")) {
            Object include = query.get("include");
            if (include instanceof List) {
                for (Object rel : (List<?>) include) {
                    if (!isValidIdentifier(rel.toString())) {
                        throw new IllegalArgumentException("Invalid include name: " + rel);
                    }
                }
            } else if (include instanceof Map) {
                for (Object key : ((Map<?, ?>) include).keySet()) {
                    if (!isValidIdentifier(key.toString())) {
                        throw new IllegalArgumentException("Invalid include name: " + key);
                    }
                }
            }
        }

        // Validate groupBy field names
        if (query.containsKey("groupBy")) {
            Object gb = query.get("groupBy");
            if (gb instanceof List) {
                for (Object g : (List<?>) gb) {
                    if (!isValidIdentifier(g.toString())) {
                        throw new IllegalArgumentException("Invalid groupBy field: " + g);
                    }
                }
            }
        }

        // Validate WHERE clause field names
        if (query.containsKey("where")) {
            validateWhereFieldNames(query.get("where"));
        }

        if (query.containsKey("aggregate")) {
            Object agg = query.get("aggregate");
            if (agg instanceof Map) {
                Map<?, ?> aggMap = (Map<?, ?>) agg;
                for (Object val : aggMap.values()) {
                    if (val instanceof Map) {
                        Map<?, ?> funcMap = (Map<?, ?>) val;
                        for (Map.Entry<?, ?> fe : funcMap.entrySet()) {
                            String funcName = fe.getKey().toString();
                            if (!funcName.matches("count|avg|sum|min|max")) {
                                throw new IllegalArgumentException(
                                        "Unknown aggregation function: " + funcName);
                            }
                            // Validate the field reference
                            if (fe.getValue() instanceof String) {
                                String fieldRef = fe.getValue().toString();
                                if (!isValidIdentifier(fieldRef)) {
                                    throw new IllegalArgumentException(
                                            "Invalid aggregate field: " + fieldRef);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Recursively validate that all field names in a WHERE clause are safe identifiers. Logical
     * operators (and, or, not) are traversed; all other keys are checked.
     */
    @SuppressWarnings("unchecked")
    private void validateWhereFieldNames(Object where) {
        if (!(where instanceof Map)) return;
        Map<?, ?> whereMap = (Map<?, ?>) where;
        for (Map.Entry<?, ?> entry : whereMap.entrySet()) {
            String key = entry.getKey().toString();
            String keyLower = key.toLowerCase();
            if ("and".equals(keyLower) || "or".equals(keyLower)) {
                Object val = entry.getValue();
                if (val instanceof List) {
                    for (Object item : (List<?>) val) {
                        validateWhereFieldNames(item);
                    }
                }
            } else if ("not".equals(keyLower)) {
                validateWhereFieldNames(entry.getValue());
            } else {
                if (!isValidIdentifier(key)) {
                    throw new IllegalArgumentException("Invalid where field: " + key);
                }
            }
        }
    }

    /**
     * SQL-safe identifier: a dot-separated path of simple identifiers. Each segment must start with
     * a letter or underscore, followed by letters, digits, or underscores. Dot notation supports
     * JSON column paths like {@code properties.material}.
     *
     * <p>Matches {@code ^[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)*$}.
     */
    static boolean isValidIdentifier(String id) {
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
