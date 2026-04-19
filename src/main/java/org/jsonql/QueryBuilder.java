package org.jsonql;

import java.util.*;

/**
 * Fluent API for building JSONQL v1.0 queries programmatically.
 *
 * <p>Usage:
 *
 * <pre>
 * Map&lt;String, Object&gt; query = new QueryBuilder()
 *     .fields("id", "name", "email")
 *     .where(Conditions.field("status", Conditions.eq("active")))
 *     .sort("name", "-created_at")
 *     .limit(10)
 *     .build();
 * </pre>
 */
public class QueryBuilder {

    private final Map<String, Object> query = new LinkedHashMap<>();

    public QueryBuilder() {
        query.put("version", "1.0");
    }

    /** Set the fields (projection) for the query. */
    public QueryBuilder fields(String... fields) {
        query.put("fields", Arrays.asList(fields));
        return this;
    }

    /** Set the WHERE clause. */
    public QueryBuilder where(Map<String, Object> where) {
        query.put("where", where);
        return this;
    }

    /** Add an AND condition to the existing WHERE clause. */
    @SuppressWarnings("unchecked")
    public QueryBuilder andWhere(Map<String, Object> condition) {
        Object existing = query.get("where");
        if (existing == null) {
            query.put("where", condition);
        } else if (existing instanceof Map) {
            Map<String, Object> existingMap = (Map<String, Object>) existing;
            if (existingMap.containsKey("and") && existingMap.get("and") instanceof List) {
                ((List<Object>) existingMap.get("and")).add(condition);
            } else {
                Map<String, Object> andClause = new LinkedHashMap<>();
                List<Object> conditions = new ArrayList<>();
                conditions.add(existing);
                conditions.add(condition);
                andClause.put("and", conditions);
                query.put("where", andClause);
            }
        }
        return this;
    }

    /** Add an OR condition to the existing WHERE clause. */
    @SuppressWarnings("unchecked")
    public QueryBuilder orWhere(Map<String, Object> condition) {
        Object existing = query.get("where");
        if (existing == null) {
            query.put("where", condition);
        } else if (existing instanceof Map) {
            Map<String, Object> existingMap = (Map<String, Object>) existing;
            if (existingMap.containsKey("or") && existingMap.get("or") instanceof List) {
                ((List<Object>) existingMap.get("or")).add(condition);
            } else {
                Map<String, Object> orClause = new LinkedHashMap<>();
                List<Object> conditions = new ArrayList<>();
                conditions.add(existing);
                conditions.add(condition);
                orClause.put("or", conditions);
                query.put("where", orClause);
            }
        }
        return this;
    }

    /** Set the sort fields. Prefix with "-" for descending order. */
    public QueryBuilder sort(String... fields) {
        if (fields.length == 1) {
            query.put("sort", fields[0]);
        } else {
            query.put("sort", Arrays.asList(fields));
        }
        return this;
    }

    /** Set the maximum number of records to return. */
    public QueryBuilder limit(int limit) {
        query.put("limit", limit);
        return this;
    }

    /** Set the number of records to skip (offset). */
    public QueryBuilder skip(int skip) {
        query.put("skip", skip);
        return this;
    }

    /** Set the GROUP BY fields. */
    public QueryBuilder groupBy(String... fields) {
        query.put("groupBy", Arrays.asList(fields));
        return this;
    }

    /** Set the aggregate definitions. */
    public QueryBuilder aggregate(Map<String, Object> aggregate) {
        query.put("aggregate", aggregate);
        return this;
    }

    /** Set the include (eager loading) definitions. */
    public QueryBuilder include(Object... relations) {
        if (relations.length == 1 && relations[0] instanceof Map) {
            query.put("include", relations[0]);
        } else {
            query.put("include", Arrays.asList(relations));
        }
        return this;
    }

    /** Enable DISTINCT selection (SELECT DISTINCT). */
    public QueryBuilder distinct() {
        query.put("distinct", true);
        return this;
    }

    /** Set DISTINCT on specific fields. */
    public QueryBuilder distinct(String... fields) {
        query.put("distinct", Arrays.asList(fields));
        return this;
    }

    /** Build and return the query as a Map. */
    public Map<String, Object> build() {
        return new LinkedHashMap<>(query);
    }

    /** Reset the builder to a clean state. */
    public QueryBuilder reset() {
        query.clear();
        query.put("version", "1.0");
        return this;
    }
}
