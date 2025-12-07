package org.jsonql;

import org.jsonql.schema.JSONQLSchema;
import org.jsonql.validator.JSONQLValidator;

import java.util.Map;
import java.util.List;

public class JSONQLParser {
    
    private JSONQLSchema schema;
    private String tableName;

    public JSONQLParser() {
    }

    public JSONQLParser(JSONQLSchema schema, String tableName) {
        this.schema = schema;
        this.tableName = tableName;
    }

    public void parse(Map<String, Object> query) throws IllegalArgumentException {
        // 1. Basic Syntax Validation
        validateSyntax(query);

        // 2. Schema & Permission Validation
        if (schema != null && tableName != null) {
            JSONQLValidator validator = new JSONQLValidator(schema, tableName);
            JSONQLValidator.ValidationResult result = validator.validate(query);
            if (!result.valid) {
                // For now, throw the first error
                throw new IllegalArgumentException(result.errors.get(0).message);
            }
        }
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
