package org.jsonql.validator;

import org.jsonql.schema.JSONQLSchema;
import org.jsonql.schema.JSONQLTableSchema;
import org.jsonql.schema.JSONQLFieldSchema;
import org.jsonql.schema.JSONQLRelation;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class JSONQLValidator {
    private final JSONQLSchema schema;
    private final String tableName;

    public static class ValidationResult {
        public boolean valid;
        public List<ValidationError> errors = new ArrayList<>();

        public ValidationResult(boolean valid) {
            this.valid = valid;
        }
    }

    public static class ValidationError {
        public String code;
        public String message;

        public ValidationError(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    public JSONQLValidator(JSONQLSchema schema, String tableName) {
        this.schema = schema;
        this.tableName = tableName;
    }

    public ValidationResult validate(Map<String, Object> query) {
        ValidationResult result = new ValidationResult(true);
        JSONQLTableSchema tableSchema = schema.tables.get(tableName);

        if (tableSchema == null) {
            // If table is not in schema, we assume it's allowed (no restrictions defined)
            // unless we want strict schema enforcement. For now, matching standard behavior of "No Schema = No Rules".
            return result;
        }

        // Validate fields
        if (query.containsKey("fields")) {
            List<?> fields = (List<?>) query.get("fields");
            if (fields.isEmpty()) {
                result.valid = false;
                result.errors.add(new ValidationError("INVALID_FIELDS", "Fields array cannot be empty"));
                return result;
            }
            for (Object f : fields) {
                String fieldName = f.toString();
                if (!validateField(fieldName, "select", tableSchema, result)) {
                    result.valid = false;
                }
            }
        }

        // Validate where
        if (query.containsKey("where")) {
            Map<?, ?> where = (Map<?, ?>) query.get("where");
            validateWhere(where, tableSchema, result);
        }

        // Validate sort
        if (query.containsKey("sort")) {
            Object sort = query.get("sort");
            if (sort instanceof List) {
                for (Object s : (List<?>) sort) {
                    String fieldName = s.toString().startsWith("-") ? s.toString().substring(1) : s.toString();
                    if (!validateField(fieldName, "sort", tableSchema, result)) {
                        result.valid = false;
                    }
                }
            } else if (sort instanceof String) {
                String fieldName = sort.toString().startsWith("-") ? sort.toString().substring(1) : sort.toString();
                if (!validateField(fieldName, "sort", tableSchema, result)) {
                    result.valid = false;
                }
            }
        }

        // Validate groupBy
        if (query.containsKey("groupBy")) {
            List<?> groupBy = (List<?>) query.get("groupBy");
            for (Object g : groupBy) {
                String fieldName = g.toString();
                if (!validateField(fieldName, "group", tableSchema, result)) {
                    result.valid = false;
                }
            }
        }

        // Validate aggregate
        if (query.containsKey("aggregate")) {
            Map<?, ?> aggregate = (Map<?, ?>) query.get("aggregate");
            for (Object val : aggregate.values()) {
                if (val instanceof Map) {
                    Map<?, ?> funcMap = (Map<?, ?>) val;
                    for (Map.Entry<?, ?> entry : funcMap.entrySet()) {
                        String func = entry.getKey().toString();
                        String fieldName = entry.getValue().toString();
                        if (!validateField(fieldName, func, tableSchema, result)) {
                            result.valid = false;
                        }
                    }
                }
            }
        }

        // Validate include
        if (query.containsKey("include")) {
            Object include = query.get("include");
            if (include instanceof List) {
                for (Object i : (List<?>) include) {
                    String relationName = i.toString();
                    if (!validateRelation(relationName, tableSchema, result)) {
                        result.valid = false;
                    }
                }
            }
        }

        return result;
    }

    private void validateWhere(Map<?, ?> where, JSONQLTableSchema tableSchema, ValidationResult result) {
        for (Map.Entry<?, ?> entry : where.entrySet()) {
            String key = entry.getKey().toString();
            if (key.equals("and") || key.equals("or")) {
                List<?> list = (List<?>) entry.getValue();
                for (Object item : list) {
                    validateWhere((Map<?, ?>) item, tableSchema, result);
                }
            } else if (key.equals("not")) {
                validateWhere((Map<?, ?>) entry.getValue(), tableSchema, result);
            } else {
                if (!validateField(key, "filter", tableSchema, result)) {
                    result.valid = false;
                }
            }
        }
    }

    private boolean validateField(String fieldName, String checkType, JSONQLTableSchema tableSchema, ValidationResult result) {
        if (tableSchema.fields == null) return true;
        JSONQLFieldSchema fieldSchema = tableSchema.fields.get(fieldName);
        if (fieldSchema == null) {
            // Check if it's a nested field or just unknown
            // For simplicity, we'll assume unknown fields are not allowed if strict schema
            // But here we just check permissions if field exists
            return true; 
        }

        boolean allowed = true;
        String errorMsg = "";

        switch (checkType) {
            case "select":
                allowed = fieldSchema.allowSelect != null ? fieldSchema.allowSelect : true;
                errorMsg = "not allowed to be selected";
                break;
            case "filter":
                allowed = fieldSchema.allowFilter != null ? fieldSchema.allowFilter : true;
                errorMsg = "not allowed to be used in filter";
                break;
            case "sort":
                allowed = fieldSchema.allowSort != null ? fieldSchema.allowSort : true;
                errorMsg = "not allowed to be used in sort";
                break;
            case "group":
                allowed = fieldSchema.allowGroup != null ? fieldSchema.allowGroup : true;
                errorMsg = "not allowed to be used in groupBy";
                break;
            case "count":
                allowed = checkAggregatePermission(fieldSchema, fieldSchema.allowCount);
                errorMsg = "not allowed to be aggregated with count";
                break;
            case "sum":
                allowed = checkAggregatePermission(fieldSchema, fieldSchema.allowSum);
                errorMsg = "not allowed to be aggregated with sum";
                break;
            case "avg":
                allowed = checkAggregatePermission(fieldSchema, fieldSchema.allowAvg);
                errorMsg = "not allowed to be aggregated with avg";
                break;
            case "min":
                allowed = checkAggregatePermission(fieldSchema, fieldSchema.allowMin);
                errorMsg = "not allowed to be aggregated with min";
                break;
            case "max":
                allowed = checkAggregatePermission(fieldSchema, fieldSchema.allowMax);
                errorMsg = "not allowed to be aggregated with max";
                break;
            default:
                // Generic aggregate check
                if (checkType.matches("count|sum|avg|min|max")) {
                     allowed = fieldSchema.allowAggregate != null ? fieldSchema.allowAggregate : true;
                     errorMsg = "not allowed to be aggregated";
                }
                break;
        }

        if (!allowed) {
            result.errors.add(new ValidationError("FIELD_NOT_ALLOWED", "Field \"" + fieldName + "\" is " + errorMsg));
            return false;
        }
        return true;
    }

    private boolean checkAggregatePermission(JSONQLFieldSchema field, Boolean specificPermission) {
        if (specificPermission != null) {
            return specificPermission;
        }
        return field.allowAggregate != null ? field.allowAggregate : true;
    }

    private boolean validateRelation(String relationName, JSONQLTableSchema tableSchema, ValidationResult result) {
        if (tableSchema.relations == null) return true;
        JSONQLRelation relation = tableSchema.relations.get(relationName);
        if (relation == null) {
             // Unknown relation
             return true;
        }
        
        if (relation.allowInclude != null && !relation.allowInclude) {
            result.errors.add(new ValidationError("RELATION_NOT_ALLOWED", "Relation \"" + relationName + "\" is not allowed to be included"));
            return false;
        }
        return true;
    }
}
