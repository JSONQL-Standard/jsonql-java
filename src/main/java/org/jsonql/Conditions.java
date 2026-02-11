package org.jsonql;

import java.util.*;

/**
 * Static helper methods for constructing JSONQL WHERE conditions.
 * <p>
 * These helpers create condition maps compatible with the JSONQL query syntax.
 * Combine them with {@link QueryBuilder} and {@link MutationBuilder} for a fluent API.
 * <p>
 * Usage:
 * <pre>
 * import static org.jsonql.Conditions.*;
 *
 * Map&lt;String, Object&gt; query = new QueryBuilder()
 *     .fields("id", "name")
 *     .where(and(
 *         field("status", eq("active")),
 *         field("age", gte(18))
 *     ))
 *     .build();
 * </pre>
 */
public final class Conditions {

    private Conditions() {} // utility class

    // ---- Comparison Operators ----

    /** Creates an equality condition: {@code {"eq": value}} */
    public static Map<String, Object> eq(Object value) {
        return Map.of("eq", value);
    }

    /** Creates a not-equal condition: {@code {"neq": value}} */
    public static Map<String, Object> neq(Object value) {
        return Map.of("neq", value);
    }

    /** Creates a greater-than condition: {@code {"gt": value}} */
    public static Map<String, Object> gt(Object value) {
        return Map.of("gt", value);
    }

    /** Creates a greater-than-or-equal condition: {@code {"gte": value}} */
    public static Map<String, Object> gte(Object value) {
        return Map.of("gte", value);
    }

    /** Creates a less-than condition: {@code {"lt": value}} */
    public static Map<String, Object> lt(Object value) {
        return Map.of("lt", value);
    }

    /** Creates a less-than-or-equal condition: {@code {"lte": value}} */
    public static Map<String, Object> lte(Object value) {
        return Map.of("lte", value);
    }

    // ---- Collection Operators ----

    /** Creates an IN condition: {@code {"in": [values...]}} */
    public static Map<String, Object> in(Object... values) {
        return Map.of("in", Arrays.asList(values));
    }

    /** Creates a NOT IN condition: {@code {"nin": [values...]}} */
    public static Map<String, Object> nin(Object... values) {
        return Map.of("nin", Arrays.asList(values));
    }

    // ---- String Operators ----

    /** Creates a LIKE condition: {@code {"like": pattern}} */
    public static Map<String, Object> like(String pattern) {
        return Map.of("like", pattern);
    }

    /** Creates a contains condition: {@code {"like": "%value%"}} */
    public static Map<String, Object> contains(String value) {
        return Map.of("like", "%" + value + "%");
    }

    /** Creates a starts-with condition: {@code {"like": "value%"}} */
    public static Map<String, Object> startsWith(String value) {
        return Map.of("like", value + "%");
    }

    /** Creates an ends-with condition: {@code {"like": "%value"}} */
    public static Map<String, Object> endsWith(String value) {
        return Map.of("like", "%" + value);
    }

    // ---- Field Condition ----

    /** Creates a field condition: {@code {fieldName: condition}} */
    public static Map<String, Object> field(String fieldName, Map<String, Object> condition) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(fieldName, condition);
        return result;
    }

    /** Creates a direct equality field condition: {@code {fieldName: value}} */
    public static Map<String, Object> field(String fieldName, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(fieldName, value);
        return result;
    }

    // ---- Logical Operators ----

    /** Creates an AND logical condition */
    @SafeVarargs
    public static Map<String, Object> and(Map<String, Object>... conditions) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("and", Arrays.asList(conditions));
        return result;
    }

    /** Creates an OR logical condition */
    @SafeVarargs
    public static Map<String, Object> or(Map<String, Object>... conditions) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("or", Arrays.asList(conditions));
        return result;
    }

    /** Creates a NOT logical condition */
    public static Map<String, Object> not(Map<String, Object> condition) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("not", condition);
        return result;
    }
}
