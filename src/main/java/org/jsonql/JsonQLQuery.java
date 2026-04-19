package org.jsonql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed representation of a JSONQL query, matching the structure used by the TypeScript, Python,
 * and Go SDKs.
 *
 * <p>Construct from a raw map via {@link #fromMap(Map)}, or build programmatically and convert back
 * to a map via {@link #toMap()}.
 *
 * <pre>{@code
 * // From raw input
 * JsonQLQuery query = JsonQLQuery.fromMap(rawMap);
 * System.out.println(query.getFields()); // ["name", "email"]
 *
 * // Programmatic construction
 * JsonQLQuery q = new JsonQLQuery();
 * q.setFields(List.of("name", "email"));
 * q.setLimit(10);
 * Map<String, Object> map = q.toMap();
 * }</pre>
 */
public class JsonQLQuery {

    private String version = "1.0";
    private String from;
    private List<String> fields;
    private Map<String, Object> where;
    private List<String> sort;
    private Integer limit;
    private Integer offset;
    private Map<String, Object> aggregate;
    private List<String> groupBy;
    private Object include; // List<String> or Map<String, Object>
    private Object distinct; // Boolean or List<String>

    public JsonQLQuery() {}

    // ── Getters & Setters ────────────────────────────────────────────

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }

    public Map<String, Object> getWhere() {
        return where;
    }

    public void setWhere(Map<String, Object> where) {
        this.where = where;
    }

    public List<String> getSort() {
        return sort;
    }

    public void setSort(List<String> sort) {
        this.sort = sort;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public Map<String, Object> getAggregate() {
        return aggregate;
    }

    public void setAggregate(Map<String, Object> aggregate) {
        this.aggregate = aggregate;
    }

    public List<String> getGroupBy() {
        return groupBy;
    }

    public void setGroupBy(List<String> groupBy) {
        this.groupBy = groupBy;
    }

    /**
     * Returns the include clause. May be a {@code List<String>} for simple includes or a {@code
     * Map<String, Object>} for nested includes with options.
     */
    public Object getInclude() {
        return include;
    }

    public void setInclude(Object include) {
        this.include = include;
    }

    /**
     * Returns the distinct clause. May be a {@code Boolean} for simple distinct or a {@code
     * List<String>} for distinct-on-columns.
     */
    public Object getDistinct() {
        return distinct;
    }

    public void setDistinct(Object distinct) {
        this.distinct = distinct;
    }

    // ── Factory ──────────────────────────────────────────────────────

    /**
     * Creates a {@code JsonQLQuery} from a raw map (e.g. from a parsed JSON body).
     *
     * @param map the raw query map
     * @return a typed query instance
     */
    @SuppressWarnings("unchecked")
    public static JsonQLQuery fromMap(Map<String, Object> map) {
        if (map == null) {
            return new JsonQLQuery();
        }
        JsonQLQuery q = new JsonQLQuery();
        if (map.containsKey("version")) {
            q.version = String.valueOf(map.get("version"));
        }
        if (map.containsKey("from")) {
            q.from = (String) map.get("from");
        }
        if (map.containsKey("fields")) {
            q.fields = toStringList(map.get("fields"));
        }
        if (map.containsKey("where")) {
            q.where = (Map<String, Object>) map.get("where");
        }
        if (map.containsKey("sort")) {
            q.sort = toStringList(map.get("sort"));
        }
        if (map.containsKey("limit")) {
            q.limit = toInteger(map.get("limit"));
        }
        // Normalise skip → offset
        if (map.containsKey("offset")) {
            q.offset = toInteger(map.get("offset"));
        } else if (map.containsKey("skip")) {
            q.offset = toInteger(map.get("skip"));
        }
        if (map.containsKey("aggregate")) {
            q.aggregate = (Map<String, Object>) map.get("aggregate");
        }
        if (map.containsKey("groupBy")) {
            q.groupBy = toStringList(map.get("groupBy"));
        }
        if (map.containsKey("include")) {
            q.include = map.get("include");
        }
        if (map.containsKey("distinct")) {
            q.distinct = map.get("distinct");
        }
        return q;
    }

    // ── Conversion ───────────────────────────────────────────────────

    /**
     * Converts this query back to a raw map, suitable for passing to {@link SQLTranspiler} or
     * serialising to JSON.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (version != null) {
            map.put("version", version);
        }
        if (from != null) {
            map.put("from", from);
        }
        if (fields != null && !fields.isEmpty()) {
            map.put("fields", new ArrayList<>(fields));
        }
        if (where != null && !where.isEmpty()) {
            map.put("where", where);
        }
        if (sort != null && !sort.isEmpty()) {
            map.put("sort", sort);
        }
        if (limit != null) {
            map.put("limit", limit);
        }
        if (offset != null) {
            map.put("offset", offset);
        }
        if (aggregate != null && !aggregate.isEmpty()) {
            map.put("aggregate", aggregate);
        }
        if (groupBy != null && !groupBy.isEmpty()) {
            map.put("groupBy", groupBy);
        }
        if (include != null) {
            map.put("include", include);
        }
        if (distinct != null) {
            map.put("distinct", distinct);
        }
        return Collections.unmodifiableMap(map);
    }

    /** Returns true if this looks like a mutation (has data, patch, or delete keys). */
    public boolean isMutation() {
        return false; // JsonQLQuery is always a read query
    }

    // ── Helpers ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object value) {
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<Object>) value) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        if (value instanceof String) {
            return new ArrayList<>(List.of((String) value));
        }
        return null;
    }

    private static Integer toInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }
}
