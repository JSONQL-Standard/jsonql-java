package org.jsonql;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import org.jsonql.dialect.GenericDialect;
import org.jsonql.schema.JsonQLSchema;
import org.jsonql.schema.JsonQLTableSchema;
import org.jsonql.schema.JsonQLFieldSchema;
import org.jsonql.schema.JsonQLRelation;

public class TranspilationTest {

    @Test
    public void testTranspilation() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        File dir = resolveTranspilationDir();
        
        Assume.assumeTrue(
            "Transpilation tests directory not found at: " + (dir != null ? dir.getAbsolutePath() : "null") + " (skipping)",
            dir != null && dir.exists()
        );

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            fail("No transpilation tests found in: " + dir.getAbsolutePath());
        }

        SQLTranspiler transpiler = new SQLTranspiler(new GenericDialect());

        for (File file : files) {
            System.out.println("Running transpilation tests from: " + file.getName());
            List<TranspilationTestCase> tests = mapper.readValue(file, new TypeReference<List<TranspilationTestCase>>(){});

            for (TranspilationTestCase tc : tests) {
                System.out.println("Running transpilation test: " + tc.id);
                JsonQLSchema schema = null;
                if (tc.schema != null) {
                    schema = parseSchema(tc.schema);
                }
                
                SQLTranspiler.TranspilationResult result = transpiler.transpile(tc.query, tc.tableName, schema);
                assertEquals(
                    "Test " + tc.id + " failed",
                    normalizeSql(tc.expectedSQL),
                    normalizeSql(result.sql)
                );
                
                if (tc.expectedArgs != null) {
                    assertEquals("Test " + tc.id + " args count mismatch", tc.expectedArgs.size(), result.parameters.size());
                    for (int i = 0; i < tc.expectedArgs.size(); i++) {
                        assertEquals("Test " + tc.id + " arg " + i + " mismatch", tc.expectedArgs.get(i), result.parameters.get(i));
                    }
                }
            }
        }
    }

    private File resolveTranspilationDir() {
        String[] candidates = new String[] {
            "../jsonql-spec/tests/transpilation",
            "../jsonql-go/tests/fixtures/transpilation",
            "tests/fixtures/transpilation"
        };

        for (String candidate : candidates) {
            File dir = new File(candidate);
            if (dir.exists() && dir.isDirectory()) {
                return dir;
            }
        }

        return new File(candidates[0]);
    }

    private String normalizeSql(String sql) {
        if (sql == null) {
            return "";
        }
        String normalized = sql
            .replace("\"", "")
            .replace("`", "")
            .replace("[", "")
            .replace("]", "")
            .replaceAll("\\s+", " ")
            .trim()
            .toLowerCase(Locale.ROOT);
        return normalized;
    }

    private JsonQLSchema parseSchema(Map<String, Object> schemaMap) {
        JsonQLSchema schema = new JsonQLSchema();
        for (Map.Entry<String, Object> entry : schemaMap.entrySet()) {
            String tableName = entry.getKey();
            Map<String, Object> tableMap = (Map<String, Object>) entry.getValue();
            JsonQLTableSchema tableSchema = new JsonQLTableSchema();
            
            if (tableMap.containsKey("fields")) {
                Map<String, Object> fieldsMap = (Map<String, Object>) tableMap.get("fields");
                for (Map.Entry<String, Object> fieldEntry : fieldsMap.entrySet()) {
                    String fieldName = fieldEntry.getKey();
                    Map<String, Object> fieldProps = (Map<String, Object>) fieldEntry.getValue();
                    String type = (String) fieldProps.get("type");
                    JsonQLFieldSchema fieldSchema = new JsonQLFieldSchema(type != null ? type : "string");
                    tableSchema.fields.put(fieldName, fieldSchema);
                }
            }

            if (tableMap.containsKey("relations")) {
                Map<String, Object> relsMap = (Map<String, Object>) tableMap.get("relations");
                for (Map.Entry<String, Object> relEntry : relsMap.entrySet()) {
                    String relName = relEntry.getKey();
                    Map<String, Object> relProps = (Map<String, Object>) relEntry.getValue();
                    JsonQLRelation relSchema = new JsonQLRelation((String) relProps.get("type"), (String) relProps.get("target"));
                    if (relProps.containsKey("foreignKey")) {
                        relSchema.foreignKey = (String) relProps.get("foreignKey");
                    }
                    tableSchema.relations.put(relName, relSchema);
                }
            }
            
            schema.tables.put(tableName, tableSchema);
        }
        return schema;
    }

    static class TranspilationTestCase {
        public String id;
        public String description;
        public String tableName;
        public Map<String, Object> schema;
        public Map<String, Object> query;
        public String expectedSQL;
        public List<Object> expectedArgs;
    }
}
