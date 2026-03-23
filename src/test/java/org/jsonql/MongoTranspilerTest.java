package org.jsonql;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for MongoTranspiler — validates operator coverage, distinct support,
 * and unknown operator rejection.
 */
public class MongoTranspilerTest {

    private final MongoTranspiler transpiler = new MongoTranspiler();

    // ── Basic operators ────────────────────────────────────────────────

    @Test
    public void testEqOperator() {
        Map<String, Object> query = Map.of(
            "fields", List.of("id", "name"),
            "where", Map.of("id", Map.of("eq", 1))
        );
        MongoResult result = transpiler.transpile(query, "users");
        assertEquals("find", result.operation);
        assertEquals(1, result.filter.get("id"));
    }

    @Test
    public void testNeqOperator() {
        Map<String, Object> query = Map.of(
            "fields", List.of("id"),
            "where", Map.of("status", Map.of("neq", "active"))
        );
        MongoResult result = transpiler.transpile(query, "users");
        Map<?, ?> statusFilter = (Map<?, ?>) result.filter.get("status");
        assertEquals("$ne", statusFilter.keySet().iterator().next());
        assertEquals("active", statusFilter.get("$ne"));
    }

    @Test
    public void testNeOperator() {
        Map<String, Object> query = Map.of(
            "fields", List.of("id"),
            "where", Map.of("status", Map.of("ne", "inactive"))
        );
        MongoResult result = transpiler.transpile(query, "users");
        Map<?, ?> statusFilter = (Map<?, ?>) result.filter.get("status");
        assertEquals("inactive", statusFilter.get("$ne"));
    }

    @Test
    public void testGtGteLtLteOperators() {
        Map<String, Object> query = Map.of(
            "fields", List.of("id"),
            "where", Map.of("age", Map.of("gt", 18, "lte", 65))
        );
        MongoResult result = transpiler.transpile(query, "users");
        Map<?, ?> ageFilter = (Map<?, ?>) result.filter.get("age");
        assertEquals(18, ageFilter.get("$gt"));
        assertEquals(65, ageFilter.get("$lte"));
    }

    @Test
    public void testInOperator() {
        Map<String, Object> query = Map.of(
            "fields", List.of("id"),
            "where", Map.of("status", Map.of("in", List.of("active", "pending")))
        );
        MongoResult result = transpiler.transpile(query, "users");
        Map<?, ?> statusFilter = (Map<?, ?>) result.filter.get("status");
        assertEquals(List.of("active", "pending"), statusFilter.get("$in"));
    }

    @Test
    public void testNinOperator() {
        Map<String, Object> query = Map.of(
            "fields", List.of("id"),
            "where", Map.of("status", Map.of("nin", List.of("banned", "deleted")))
        );
        MongoResult result = transpiler.transpile(query, "users");
        Map<?, ?> statusFilter = (Map<?, ?>) result.filter.get("status");
        assertEquals(List.of("banned", "deleted"), statusFilter.get("$nin"));
    }

    @Test
    public void testLikeOperator() {
        Map<String, Object> query = Map.of(
            "fields", List.of("id"),
            "where", Map.of("name", Map.of("like", "%alice%"))
        );
        MongoResult result = transpiler.transpile(query, "users");
        Map<?, ?> nameFilter = (Map<?, ?>) result.filter.get("name");
        assertEquals(".*alice.*", nameFilter.get("$regex"));
        assertEquals("i", nameFilter.get("$options"));
    }

    // ── New string operators ───────────────────────────────────────────

    @Test
    public void testContainsOperator() {
        Map<String, Object> query = Map.of(
            "fields", List.of("id"),
            "where", Map.of("name", Map.of("contains", "alice"))
        );
        MongoResult result = transpiler.transpile(query, "users");
        Map<?, ?> nameFilter = (Map<?, ?>) result.filter.get("name");
        assertNotNull(nameFilter.get("$regex"));
        assertEquals("i", nameFilter.get("$options"));
    }

    @Test
    public void testStartsOperator() {
        Map<String, Object> query = Map.of(
            "fields", List.of("id"),
            "where", Map.of("name", Map.of("starts", "Al"))
        );
        MongoResult result = transpiler.transpile(query, "users");
        Map<?, ?> nameFilter = (Map<?, ?>) result.filter.get("name");
        String regex = nameFilter.get("$regex").toString();
        assertTrue("starts regex should start with ^", regex.startsWith("^"));
    }

    @Test
    public void testEndsOperator() {
        Map<String, Object> query = Map.of(
            "fields", List.of("id"),
            "where", Map.of("name", Map.of("ends", "son"))
        );
        MongoResult result = transpiler.transpile(query, "users");
        Map<?, ?> nameFilter = (Map<?, ?>) result.filter.get("name");
        String regex = nameFilter.get("$regex").toString();
        assertTrue("ends regex should end with $", regex.endsWith("$"));
    }

    // ── Logical operators ──────────────────────────────────────────────

    @Test
    public void testOrOperator() {
        Map<String, Object> query = Map.of(
            "fields", List.of("id"),
            "where", Map.of("or", List.of(
                Map.of("status", Map.of("eq", "active")),
                Map.of("age", Map.of("gt", 30))
            ))
        );
        MongoResult result = transpiler.transpile(query, "users");
        assertNotNull(result.filter.get("$or"));
        List<?> orList = (List<?>) result.filter.get("$or");
        assertEquals(2, orList.size());
    }

    @Test
    public void testAndOperator() {
        Map<String, Object> query = Map.of(
            "fields", List.of("id"),
            "where", Map.of("and", List.of(
                Map.of("status", Map.of("eq", "active")),
                Map.of("age", Map.of("gt", 18))
            ))
        );
        MongoResult result = transpiler.transpile(query, "users");
        assertNotNull(result.filter.get("$and"));
        List<?> andList = (List<?>) result.filter.get("$and");
        assertEquals(2, andList.size());
    }

    @Test
    public void testNotOperator() {
        Map<String, Object> query = Map.of(
            "fields", List.of("id"),
            "where", Map.of("not", Map.of("status", Map.of("eq", "banned")))
        );
        MongoResult result = transpiler.transpile(query, "users");
        assertNotNull(result.filter.get("$nor"));
        List<?> norList = (List<?>) result.filter.get("$nor");
        assertEquals(1, norList.size());
    }

    // ── Distinct ───────────────────────────────────────────────────────

    @Test
    public void testDistinctBoolean() {
        Map<String, Object> query = new HashMap<>();
        query.put("fields", List.of("status"));
        query.put("distinct", true);
        query.put("sort", "status");

        MongoResult result = transpiler.transpile(query, "users");
        assertEquals("aggregate", result.operation);
        assertNotNull(result.pipeline);
        assertFalse("Pipeline should not be empty", result.pipeline.isEmpty());

        // Check for $group stage
        boolean hasGroup = result.pipeline.stream()
                .anyMatch(s -> s.containsKey("$group"));
        assertTrue("Pipeline should have $group stage", hasGroup);

        // Check for $project stage
        boolean hasProject = result.pipeline.stream()
                .anyMatch(s -> s.containsKey("$project"));
        assertTrue("Pipeline should have $project stage", hasProject);
    }

    @Test
    public void testDistinctWithFields() {
        Map<String, Object> distinctMap = Map.of("fields", List.of("status", "role"));
        Map<String, Object> query = new HashMap<>();
        query.put("fields", List.of("status", "role"));
        query.put("distinct", distinctMap);

        MongoResult result = transpiler.transpile(query, "users");
        assertEquals("aggregate", result.operation);
        assertNotNull(result.pipeline);
    }

    // ── Unknown operator validation ────────────────────────────────────

    @Test(expected = JsonQLTranspileException.class)
    public void testUnknownOperatorThrows() {
        Map<String, Object> query = Map.of(
            "fields", List.of("id"),
            "where", Map.of("name", Map.of("fuzzy", "alice"))
        );
        transpiler.transpile(query, "users");
    }

    @Test(expected = JsonQLTranspileException.class)
    public void testUnknownOperatorRegex() {
        Map<String, Object> query = Map.of(
            "fields", List.of("id"),
            "where", Map.of("name", Map.of("$regex", "alice"))
        );
        transpiler.transpile(query, "users");
    }

    // ── Sort, limit, skip ──────────────────────────────────────────────

    @Test
    public void testSortLimitSkip() {
        Map<String, Object> query = new HashMap<>();
        query.put("fields", List.of("id", "name"));
        query.put("sort", List.of("-age", "name"));
        query.put("limit", 10);
        query.put("skip", 5);

        MongoResult result = transpiler.transpile(query, "users");
        assertEquals(-1, result.sort.get("age"));
        assertEquals(1, result.sort.get("name"));
        assertEquals(10, result.limit);
        assertEquals(5, result.skip);
    }

    // ── Mutations ──────────────────────────────────────────────────────

    @Test
    public void testTranspileInsert() {
        Map<String, Object> data = Map.of("name", "Alice", "age", 30);
        MongoResult result = transpiler.transpileInsert(data, "users");
        assertEquals("insertOne", result.operation);
        assertEquals("users", result.collection);
        assertEquals("Alice", result.document.get("name"));
    }

    @Test
    public void testTranspileUpdate() {
        Map<String, Object> data = Map.of("status", "active");
        Map<String, Object> where = Map.of("id", Map.of("eq", 1));
        MongoResult result = transpiler.transpileUpdate(data, where, "users");
        assertEquals("updateMany", result.operation);
        assertNotNull(result.update);
    }

    @Test
    public void testTranspileDelete() {
        Map<String, Object> where = Map.of("id", Map.of("eq", 1));
        MongoResult result = transpiler.transpileDelete(where, "users");
        assertEquals("deleteMany", result.operation);
        assertEquals(1, result.filter.get("id"));
    }

    // ── Projection ─────────────────────────────────────────────────────

    @Test
    public void testFieldsProjection() {
        Map<String, Object> query = Map.of("fields", List.of("id", "name", "email"));
        MongoResult result = transpiler.transpile(query, "users");
        assertEquals(1, result.projection.get("id"));
        assertEquals(1, result.projection.get("name"));
        assertEquals(1, result.projection.get("email"));
    }
}
