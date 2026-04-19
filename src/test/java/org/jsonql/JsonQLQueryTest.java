package org.jsonql;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

/** Unit tests for {@link JsonQLQuery}: fromMap(), toMap(), field access, and round-tripping. */
public class JsonQLQueryTest {

    // ── fromMap basic tests ─────────────────────────────────────────

    @Test
    public void testFromMapNull() {
        JsonQLQuery q = JsonQLQuery.fromMap(null);
        assertNotNull(q);
        assertEquals("1.0", q.getVersion());
    }

    @Test
    public void testFromMapEmpty() {
        JsonQLQuery q = JsonQLQuery.fromMap(Collections.emptyMap());
        assertEquals("1.0", q.getVersion());
        assertNull(q.getFields());
        assertNull(q.getWhere());
        assertNull(q.getSort());
        assertNull(q.getLimit());
        assertNull(q.getOffset());
    }

    @Test
    public void testFromMapWithFields() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fields", Arrays.asList("name", "email"));
        JsonQLQuery q = JsonQLQuery.fromMap(map);
        assertEquals(Arrays.asList("name", "email"), q.getFields());
    }

    @Test
    public void testFromMapWithSingleFieldString() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fields", "name");
        JsonQLQuery q = JsonQLQuery.fromMap(map);
        assertEquals(Collections.singletonList("name"), q.getFields());
    }

    @Test
    public void testFromMapWithWhere() {
        Map<String, Object> where = new LinkedHashMap<>();
        where.put("age", Map.of("$gt", 18));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("where", where);

        JsonQLQuery q = JsonQLQuery.fromMap(map);
        assertNotNull(q.getWhere());
        assertTrue(q.getWhere().containsKey("age"));
    }

    @Test
    public void testFromMapWithSort() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sort", Arrays.asList("-age", "name"));
        JsonQLQuery q = JsonQLQuery.fromMap(map);
        assertEquals(Arrays.asList("-age", "name"), q.getSort());
    }

    @Test
    public void testFromMapWithSortString() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sort", "-age");
        JsonQLQuery q = JsonQLQuery.fromMap(map);
        assertEquals(Collections.singletonList("-age"), q.getSort());
    }

    @Test
    public void testFromMapWithLimitAndOffset() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("limit", 10);
        map.put("offset", 20);
        JsonQLQuery q = JsonQLQuery.fromMap(map);
        assertEquals(Integer.valueOf(10), q.getLimit());
        assertEquals(Integer.valueOf(20), q.getOffset());
    }

    @Test
    public void testFromMapWithFloatLimit() {
        // JSON parsers often produce Double for numbers
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("limit", 10.0);
        JsonQLQuery q = JsonQLQuery.fromMap(map);
        assertEquals(Integer.valueOf(10), q.getLimit());
    }

    @Test
    public void testFromMapSkipNormalisedToOffset() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("skip", 5);
        JsonQLQuery q = JsonQLQuery.fromMap(map);
        assertEquals(Integer.valueOf(5), q.getOffset());
    }

    @Test
    public void testFromMapOffsetTakesPrecedenceOverSkip() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("offset", 10);
        map.put("skip", 5);
        JsonQLQuery q = JsonQLQuery.fromMap(map);
        assertEquals(Integer.valueOf(10), q.getOffset());
    }

    @Test
    public void testFromMapWithGroupByAndAggregate() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("groupBy", Arrays.asList("department"));
        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put("count", "*");
        map.put("aggregate", agg);

        JsonQLQuery q = JsonQLQuery.fromMap(map);
        assertEquals(Collections.singletonList("department"), q.getGroupBy());
        assertNotNull(q.getAggregate());
        assertEquals("*", q.getAggregate().get("count"));
    }

    @Test
    public void testFromMapWithVersion() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("version", "1.1");
        JsonQLQuery q = JsonQLQuery.fromMap(map);
        assertEquals("1.1", q.getVersion());
    }

    @Test
    public void testFromMapWithFrom() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("from", "users");
        JsonQLQuery q = JsonQLQuery.fromMap(map);
        assertEquals("users", q.getFrom());
    }

    @Test
    public void testFromMapWithDistinctBoolean() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("distinct", true);
        JsonQLQuery q = JsonQLQuery.fromMap(map);
        assertEquals(Boolean.TRUE, q.getDistinct());
    }

    @Test
    public void testFromMapWithDistinctColumns() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("distinct", Arrays.asList("name", "email"));
        JsonQLQuery q = JsonQLQuery.fromMap(map);
        assertTrue(q.getDistinct() instanceof List);
    }

    @Test
    public void testFromMapWithIncludeList() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("include", Arrays.asList("posts", "comments"));
        JsonQLQuery q = JsonQLQuery.fromMap(map);
        assertTrue(q.getInclude() instanceof List);
    }

    @Test
    public void testFromMapWithIncludeMap() {
        Map<String, Object> includeMap = new LinkedHashMap<>();
        includeMap.put("posts", Map.of("fields", Arrays.asList("title")));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("include", includeMap);
        JsonQLQuery q = JsonQLQuery.fromMap(map);
        assertTrue(q.getInclude() instanceof Map);
    }

    // ── toMap tests ─────────────────────────────────────────────────

    @Test
    public void testToMapMinimal() {
        JsonQLQuery q = new JsonQLQuery();
        Map<String, Object> map = q.toMap();
        assertTrue(map.containsKey("version"));
        assertEquals("1.0", map.get("version"));
    }

    @Test
    public void testToMapWithAllFields() {
        JsonQLQuery q = new JsonQLQuery();
        q.setVersion("1.1");
        q.setFrom("users");
        q.setFields(Arrays.asList("name", "email"));
        q.setWhere(Map.of("age", Map.of("$gt", 18)));
        q.setSort(Arrays.asList("-age"));
        q.setLimit(10);
        q.setOffset(5);
        q.setGroupBy(Arrays.asList("department"));
        q.setAggregate(Map.of("count", "*"));
        q.setInclude(Arrays.asList("posts"));
        q.setDistinct(true);

        Map<String, Object> map = q.toMap();
        assertEquals("1.1", map.get("version"));
        assertEquals("users", map.get("from"));
        assertEquals(Arrays.asList("name", "email"), map.get("fields"));
        assertNotNull(map.get("where"));
        assertEquals(Arrays.asList("-age"), map.get("sort"));
        assertEquals(10, map.get("limit"));
        assertEquals(5, map.get("offset"));
        assertEquals(Arrays.asList("department"), map.get("groupBy"));
        assertEquals("*", ((Map<?, ?>) map.get("aggregate")).get("count"));
        assertTrue(map.containsKey("include"));
        assertEquals(Boolean.TRUE, map.get("distinct"));
    }

    @Test
    public void testToMapIsUnmodifiable() {
        JsonQLQuery q = new JsonQLQuery();
        Map<String, Object> map = q.toMap();
        try {
            map.put("evil", "value");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    // ── Round-tripping ──────────────────────────────────────────────

    @Test
    public void testRoundTrip() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("version", "1.1");
        original.put("fields", Arrays.asList("name", "email"));
        original.put("where", Map.of("name", "Alice"));
        original.put("sort", Arrays.asList("-age"));
        original.put("limit", 10);
        original.put("offset", 5);

        JsonQLQuery q = JsonQLQuery.fromMap(original);
        Map<String, Object> roundTripped = q.toMap();

        assertEquals(original.get("version"), roundTripped.get("version"));
        assertEquals(original.get("fields"), roundTripped.get("fields"));
        assertEquals(original.get("where"), roundTripped.get("where"));
        assertEquals(original.get("sort"), roundTripped.get("sort"));
        assertEquals(10, roundTripped.get("limit"));
        assertEquals(5, roundTripped.get("offset"));
    }

    // ── isMutation ──────────────────────────────────────────────────

    @Test
    public void testIsMutationAlwaysFalse() {
        JsonQLQuery q = new JsonQLQuery();
        assertFalse(q.isMutation());
    }

    // ── Setter tests ────────────────────────────────────────────────

    @Test
    public void testSettersAndGetters() {
        JsonQLQuery q = new JsonQLQuery();
        q.setVersion("1.1");
        q.setFrom("orders");
        q.setFields(List.of("total"));
        q.setLimit(50);
        q.setOffset(10);
        q.setSort(List.of("total"));
        q.setGroupBy(List.of("status"));
        q.setAggregate(Map.of("sum", "total"));
        q.setInclude(List.of("items"));
        q.setDistinct(List.of("status"));

        assertEquals("1.1", q.getVersion());
        assertEquals("orders", q.getFrom());
        assertEquals(List.of("total"), q.getFields());
        assertEquals(Integer.valueOf(50), q.getLimit());
        assertEquals(Integer.valueOf(10), q.getOffset());
        assertEquals(List.of("total"), q.getSort());
        assertEquals(List.of("status"), q.getGroupBy());
        assertEquals("total", q.getAggregate().get("sum"));
        assertEquals(List.of("items"), q.getInclude());
        assertEquals(List.of("status"), q.getDistinct());
    }
}
