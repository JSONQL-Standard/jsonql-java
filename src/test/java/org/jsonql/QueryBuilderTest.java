package org.jsonql;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Unit tests for QueryBuilder — covers all builder methods including v1.1 features.
 */
public class QueryBuilderTest {

    @Test
    public void testBasicBuild() {
        Map<String, Object> query = new QueryBuilder().build();
        assertEquals("1.0", query.get("version"));
    }

    @Test
    public void testFields() {
        Map<String, Object> query = new QueryBuilder()
            .fields("id", "name", "email")
            .build();
        assertEquals(Arrays.asList("id", "name", "email"), query.get("fields"));
    }

    @Test
    public void testWhere() {
        Map<String, Object> query = new QueryBuilder()
            .where(Conditions.field("status", Conditions.eq("active")))
            .build();
        @SuppressWarnings("unchecked")
        Map<String, Object> where = (Map<String, Object>) query.get("where");
        assertNotNull(where);
        @SuppressWarnings("unchecked")
        Map<String, Object> statusCond = (Map<String, Object>) where.get("status");
        assertEquals("active", statusCond.get("eq"));
    }

    @Test
    public void testAndWhere() {
        Map<String, Object> query = new QueryBuilder()
            .where(Conditions.field("status", Conditions.eq("active")))
            .andWhere(Conditions.field("age", Conditions.gt(18)))
            .build();
        @SuppressWarnings("unchecked")
        Map<String, Object> where = (Map<String, Object>) query.get("where");
        assertNotNull(where.get("and"));
    }

    @Test
    public void testOrWhere() {
        Map<String, Object> query = new QueryBuilder()
            .where(Conditions.field("role", Conditions.eq("admin")))
            .orWhere(Conditions.field("role", Conditions.eq("moderator")))
            .build();
        @SuppressWarnings("unchecked")
        Map<String, Object> where = (Map<String, Object>) query.get("where");
        assertNotNull(where.get("or"));
    }

    @Test
    public void testSort() {
        Map<String, Object> query = new QueryBuilder()
            .sort("name", "-created_at")
            .build();
        assertEquals(Arrays.asList("name", "-created_at"), query.get("sort"));
    }

    @Test
    public void testSingleSort() {
        Map<String, Object> query = new QueryBuilder()
            .sort("name")
            .build();
        assertEquals("name", query.get("sort"));
    }

    @Test
    public void testLimitAndSkip() {
        Map<String, Object> query = new QueryBuilder()
            .limit(10)
            .skip(20)
            .build();
        assertEquals(10, query.get("limit"));
        assertEquals(20, query.get("skip"));
    }

    @Test
    public void testGroupBy() {
        Map<String, Object> query = new QueryBuilder()
            .groupBy("category", "status")
            .build();
        assertEquals(Arrays.asList("category", "status"), query.get("groupBy"));
    }

    @Test
    public void testAggregate() {
        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put("total", Collections.singletonMap("count", "id"));
        agg.put("avg_price", Collections.singletonMap("avg", "price"));

        Map<String, Object> query = new QueryBuilder()
            .aggregate(agg)
            .build();
        assertEquals(agg, query.get("aggregate"));
    }

    @Test
    public void testGroupByWithAggregate() {
        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put("count", Collections.singletonMap("count", "id"));

        Map<String, Object> query = new QueryBuilder()
            .groupBy("category")
            .aggregate(agg)
            .build();
        assertEquals(Arrays.asList("category"), query.get("groupBy"));
        assertNotNull(query.get("aggregate"));
    }

    @Test
    public void testDistinctBoolean() {
        Map<String, Object> query = new QueryBuilder()
            .fields("category")
            .distinct()
            .build();
        assertEquals(true, query.get("distinct"));
    }

    @Test
    public void testDistinctFields() {
        Map<String, Object> query = new QueryBuilder()
            .distinct("category", "status")
            .build();
        assertEquals(Arrays.asList("category", "status"), query.get("distinct"));
    }

    @Test
    public void testIncludeArray() {
        Map<String, Object> query = new QueryBuilder()
            .include("posts", "comments")
            .build();
        assertEquals(Arrays.asList("posts", "comments"), query.get("include"));
    }

    @Test
    public void testIncludeMap() {
        Map<String, Object> postsInclude = new LinkedHashMap<>();
        postsInclude.put("fields", Arrays.asList("id", "title"));

        Map<String, Object> includeMap = new LinkedHashMap<>();
        includeMap.put("posts", postsInclude);

        Map<String, Object> query = new QueryBuilder()
            .include(includeMap)
            .build();
        assertTrue(query.get("include") instanceof Map);
    }

    @Test
    public void testReset() {
        QueryBuilder builder = new QueryBuilder();
        builder.fields("id").limit(10);
        Map<String, Object> q1 = builder.build();
        assertNotNull(q1.get("fields"));

        builder.reset();
        Map<String, Object> q2 = builder.build();
        assertNull(q2.get("fields"));
        assertEquals("1.0", q2.get("version"));
    }

    @Test
    public void testComplexQuery() {
        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put("total_orders", Collections.singletonMap("count", "id"));
        agg.put("total_revenue", Collections.singletonMap("sum", "total"));

        Map<String, Object> query = new QueryBuilder()
            .fields("status")
            .where(Conditions.field("status", Conditions.in("completed", "pending")))
            .groupBy("status")
            .aggregate(agg)
            .sort("status")
            .limit(100)
            .build();

        assertNotNull(query.get("fields"));
        assertNotNull(query.get("where"));
        assertNotNull(query.get("groupBy"));
        assertNotNull(query.get("aggregate"));
        assertNotNull(query.get("sort"));
        assertEquals(100, query.get("limit"));
    }
}
