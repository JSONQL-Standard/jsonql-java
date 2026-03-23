package org.jsonql;

import org.jsonql.schema.JsonQLSchema;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Tests for JsonQLFactory — validates envOr, loadSchema, mustLoadSchema.
 */
public class FactoryTest {

    // ── envOr ──────────────────────────────────────────────────────────

    @Test
    public void testEnvOrReturnsFallbackWhenUnset() {
        String result = JsonQLFactory.envOr("JSONQL_TEST_NEVER_SET_12345", "fallback_value");
        assertEquals("fallback_value", result);
    }

    @Test
    public void testEnvOrReturnsEnvValueWhenSet() {
        // PATH is always set
        String result = JsonQLFactory.envOr("PATH", "fallback");
        assertNotEquals("fallback", result);
        assertFalse(result.isEmpty());
    }

    // ── loadSchema ─────────────────────────────────────────────────────

    @Test
    public void testLoadSchemaValid() throws IOException {
        File tmpFile = File.createTempFile("jsonql-schema-", ".json");
        tmpFile.deleteOnExit();
        try (FileWriter w = new FileWriter(tmpFile)) {
            w.write("{\"tables\":{\"users\":{\"fields\":{\"id\":{\"type\":\"integer\"},\"name\":{\"type\":\"string\"}}}}}");
        }

        JsonQLSchema schema = JsonQLFactory.loadSchema(tmpFile.getAbsolutePath());
        assertNotNull(schema);
        assertNotNull(schema.tables.get("users"));
        assertEquals(2, schema.tables.get("users").fields.size());
    }

    @Test
    public void testLoadSchemaReturnsNullForMissingFile() {
        JsonQLSchema schema = JsonQLFactory.loadSchema("/tmp/nonexistent-schema-99999.json");
        assertNull(schema);
    }

    @Test
    public void testLoadSchemaReturnsNullForInvalidJson() throws IOException {
        File tmpFile = File.createTempFile("jsonql-bad-schema-", ".json");
        tmpFile.deleteOnExit();
        try (FileWriter w = new FileWriter(tmpFile)) {
            w.write("{broken json");
        }

        JsonQLSchema schema = JsonQLFactory.loadSchema(tmpFile.getAbsolutePath());
        assertNull(schema);
    }

    // ── mustLoadSchema ─────────────────────────────────────────────────

    @Test
    public void testMustLoadSchemaSuccess() throws IOException {
        File tmpFile = File.createTempFile("jsonql-schema-", ".json");
        tmpFile.deleteOnExit();
        try (FileWriter w = new FileWriter(tmpFile)) {
            w.write("{\"tables\":{\"products\":{\"fields\":{\"name\":{\"type\":\"string\"}}}}}");
        }

        JsonQLSchema schema = JsonQLFactory.mustLoadSchema(tmpFile.getAbsolutePath());
        assertNotNull(schema);
        assertNotNull(schema.tables.get("products"));
    }

    @Test(expected = RuntimeException.class)
    public void testMustLoadSchemaThrowsOnMissing() {
        JsonQLFactory.mustLoadSchema("/tmp/nonexistent-schema-99999.json");
    }

    @Test(expected = RuntimeException.class)
    public void testMustLoadSchemaThrowsOnInvalidJson() throws IOException {
        File tmpFile = File.createTempFile("jsonql-bad-schema-", ".json");
        tmpFile.deleteOnExit();
        try (FileWriter w = new FileWriter(tmpFile)) {
            w.write("{broken json");
        }
        JsonQLFactory.mustLoadSchema(tmpFile.getAbsolutePath());
    }
}
