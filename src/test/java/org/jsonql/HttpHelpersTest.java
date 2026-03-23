package org.jsonql;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for JsonQLHttpHelpers — validates inferMutation, getIdFromQuery,
 * and buildRestMutation.
 */
public class HttpHelpersTest {

    // ── inferMutation ──────────────────────────────────────────────────

    @Test
    public void testInferMutationPostWithData() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("data", Map.of("name", "Alice"));
        JsonQLHttpHelpers.inferMutation("POST", raw);
        assertEquals("create", raw.get("op"));
    }

    @Test
    public void testInferMutationPostWithoutDataIsQuery() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("fields", java.util.List.of("id", "name"));
        JsonQLHttpHelpers.inferMutation("POST", raw);
        assertNull("POST without data should not set op", raw.get("op"));
    }

    @Test
    public void testInferMutationPatchSetsUpdate() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("patch", Map.of("name", "Bob"));
        raw.put("where", Map.of("id", Map.of("eq", 1)));
        JsonQLHttpHelpers.inferMutation("PATCH", raw);
        assertEquals("update", raw.get("op"));
    }

    @Test
    public void testInferMutationDeleteSetsDelete() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("where", Map.of("id", Map.of("eq", 1)));
        JsonQLHttpHelpers.inferMutation("DELETE", raw);
        assertEquals("delete", raw.get("op"));
    }

    @Test
    public void testInferMutationExplicitOpPreserved() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("op", "create");
        raw.put("data", Map.of("name", "Alice"));
        JsonQLHttpHelpers.inferMutation("DELETE", raw);
        assertEquals("Existing op should not be overwritten", "create", raw.get("op"));
    }

    @Test
    public void testInferMutationExplicitCreateKey() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("create", Map.of("name", "Alice"));
        JsonQLHttpHelpers.inferMutation("POST", raw);
        assertEquals("create", raw.get("op"));
        assertEquals(Map.of("name", "Alice"), raw.get("data"));
    }

    @Test
    public void testInferMutationExplicitUpdateKey() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("update", Map.of("name", "Bob"));
        JsonQLHttpHelpers.inferMutation("PATCH", raw);
        assertEquals("update", raw.get("op"));
        assertEquals(Map.of("name", "Bob"), raw.get("patch"));
    }

    // ── getIdFromQuery ─────────────────────────────────────────────────

    @Test
    public void testGetIdFromQueryNumeric() {
        Map<String, String> params = Map.of("id", "42");
        Object id = JsonQLHttpHelpers.getIdFromQuery(params);
        assertEquals(42, id);
    }

    @Test
    public void testGetIdFromQueryString() {
        Map<String, String> params = Map.of("id", "abc-123");
        Object id = JsonQLHttpHelpers.getIdFromQuery(params);
        assertEquals("abc-123", id);
    }

    @Test
    public void testGetIdFromQueryMissing() {
        Map<String, String> params = Map.of("page", "1");
        Object id = JsonQLHttpHelpers.getIdFromQuery(params);
        assertNull(id);
    }

    @Test
    public void testGetIdFromQueryNull() {
        Object id = JsonQLHttpHelpers.getIdFromQuery(null);
        assertNull(id);
    }

    // ── buildRestMutation ──────────────────────────────────────────────

    @Test
    public void testBuildRestMutationPostCreate() {
        Map<String, Object> body = new HashMap<>();
        body.put("data", Map.of("name", "Alice"));
        Map<String, Object> result = JsonQLHttpHelpers.buildRestMutation("POST", null, body);
        assertNotNull(result);
        assertEquals("create", result.get("op"));
    }

    @Test
    public void testBuildRestMutationPatchUpdate() {
        Map<String, String> params = Map.of("id", "5");
        Map<String, Object> body = new HashMap<>();
        body.put("patch", Map.of("name", "Bob"));
        Map<String, Object> result = JsonQLHttpHelpers.buildRestMutation("PATCH", params, body);
        assertNotNull(result);
        assertEquals("update", result.get("op"));
        assertNotNull("Should have where clause with id", result.get("where"));
    }

    @Test
    public void testBuildRestMutationDeleteWithId() {
        Map<String, String> params = Map.of("id", "10");
        Map<String, Object> result = JsonQLHttpHelpers.buildRestMutation("DELETE", params, null);
        assertNotNull(result);
        assertEquals("delete", result.get("op"));
        assertNotNull(result.get("where"));
    }

    @Test
    public void testBuildRestMutationGetReturnsNull() {
        Map<String, Object> result = JsonQLHttpHelpers.buildRestMutation("GET", null, null);
        assertNull(result);
    }
}
