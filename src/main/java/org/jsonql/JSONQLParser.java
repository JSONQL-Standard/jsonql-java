package org.jsonql;

import org.jsonql.schema.JSONQLSchema;
import org.jsonql.validator.JSONQLValidator;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class JSONQLParser {
    
    private JSONQLSchema schema;
    private String tableName;
    private JsonQLParserOptions options;

    public JSONQLParser() {
    }

    public JSONQLParser(JSONQLSchema schema, String tableName) {
        this.schema = schema;
        this.tableName = tableName;
    }

    public JSONQLParser(JSONQLSchema schema, String tableName, JsonQLParserOptions options) {
        this.schema = schema;
        this.tableName = tableName;
        this.options = options;
    }

    public JSONQLParser(JsonQLParserOptions options) {
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
            JSONQLValidator validator = new JSONQLValidator(schema, tableName);
            JSONQLValidator.ValidationResult result = validator.validate(query);
            if (!result.valid) {
                // For now, throw the first error
                throw new IllegalArgumentException(result.errors.get(0).message);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void enforceOptions(Map<String, Object> query) {
        // MaxLimit enforcement
        if (options.getMaxLimit() > 0 && query.containsKey("limit")) {
            Object limitObj = query.get("limit");
            int limit = (limitObj instanceof Number) ? ((Number) limitObj).intValue() : 0;
            if (limit > options.getMaxLimit()) {
                throw new IllegalArgumentException(
                    String.format("limit %d exceeds maximum allowed limit of %d", limit, options.getMaxLimit()));
            }
        }

        // MaxNestingDepth enforcement
        if (options.getMaxNestingDepth() > 0 && query.containsKey("include")) {
            int depth = calculateIncludeDepth(query.get("include"));
            if (depth > options.getMaxNestingDepth()) {
                throw new IllegalArgumentException(
                    String.format("include nesting depth %d exceeds maximum allowed depth of %d",
                        depth, options.getMaxNestingDepth()));
            }
        }

        // AllowedFields enforcement
        if (options.getAllowedFields() != null && !options.getAllowedFields().isEmpty() && query.containsKey("fields")) {
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
        if (options.getAllowedIncludes() != null && !options.getAllowedIncludes().isEmpty() && query.containsKey("include")) {
            Set<String> allowed = new HashSet<>(options.getAllowedIncludes());
            Object include = query.get("include");
            if (include instanceof Map) {
                for (Object key : ((Map<?, ?>) include).keySet()) {
                    if (!allowed.contains(key.toString())) {
                        throw new IllegalArgumentException(
                            String.format("include '%s' is not in the allowed includes list", key));
                    }
                }
            } else if (include instanceof List) {
                for (Object item : (List<?>) include) {
                    if (!allowed.contains(item.toString())) {
                        throw new IllegalArgumentException(
                            String.format("include '%s' is not in the allowed includes list", item));
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

    private void validateSyntax(Map<String, Object> query) {
        if (query.containsKey("version")) {
            Object v = query.get("version");
            if (!"1.0".equals(v) && !"1.1".equals(v)) {
                throw new IllegalArgumentException("Query version must be \"1.0\" or \"1.1\"");
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

        if (query.containsKey("sort")) {
            Object sort = query.get("sort");
            if (sort instanceof String) {
                String s = (String) sort;
                String field = s;
                if (s.startsWith("-")) {
                    field = s.substring(1);
                }
                if (!isValidIdentifier(field)) {
                    throw new IllegalArgumentException("Invalid sort field");
                }
            }
        }

        if (query.containsKey("aggregate")) {
            Object agg = query.get("aggregate");
            if (agg instanceof Map) {
                Map<?, ?> aggMap = (Map<?, ?>) agg;
                for (Object val : aggMap.values()) {
                    if (val instanceof Map) {
                        Map<?, ?> funcMap = (Map<?, ?>) val;
                        for (Object k : funcMap.keySet()) {
                            String funcName = k.toString();
                            if (!funcName.matches("count|avg|sum|min|max")) {
                                throw new IllegalArgumentException("Unknown aggregation function: " + funcName);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isValidIdentifier(String id) {
        if (id == null || id.isEmpty()) return false;
        for (char c : id.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }
}
