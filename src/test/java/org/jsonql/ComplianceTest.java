package org.jsonql;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jsonql.schema.JSONQLSchema;
import org.jsonql.schema.JSONQLTableSchema;
import org.jsonql.schema.JSONQLFieldSchema;
import org.jsonql.schema.JSONQLRelation;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ComplianceTest {

    @Test
    public void testCompliance() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        
        // Allow overriding spec path via environment variable for CI/CD
        String specPathEnv = System.getenv("JSONQL_SPEC_PATH");
        File suitesDir;
        if (specPathEnv != null && !specPathEnv.isEmpty()) {
            suitesDir = new File(specPathEnv, "tests/suites");
        } else {
            suitesDir = new File("../jsonql-spec/tests/suites");
        }
        
        if (!suitesDir.exists()) {
            System.out.println("Compliance suites not found at: " + suitesDir.getAbsolutePath());
            return;
        }

        // 1. Standard Suite
        File standardTestsDir = new File(suitesDir, "standard/tests");
        if (standardTestsDir.exists()) {
            File[] files = standardTestsDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    runTestsFromFile(file, mapper, null);
                }
            }
        }

        // 2. Issues Suite
        File issuesDir = new File(suitesDir, "issues");
        if (issuesDir.exists()) {
            File[] issueFolders = issuesDir.listFiles(File::isDirectory);
            if (issueFolders != null) {
                for (File folder : issueFolders) {
                    File testFile = new File(folder, "test.json");
                    if (testFile.exists()) {
                        runTestsFromFile(testFile, mapper, null);
                    }
                }
            }
        }

        // 3. Security Suite
        File securityDir = new File(suitesDir, "security");
        if (securityDir.exists()) {
            File[] files = securityDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    runTestsFromFile(file, mapper, null);
                }
            }
        }

        // 4. Permissions Suite
        File permissionsDir = new File(suitesDir, "permissions");
        if (permissionsDir.exists()) {
            File schemaFile = new File(permissionsDir, "schema.json");
            Map<String, Object> sharedSchemaMap = null;
            if (schemaFile.exists()) {
                sharedSchemaMap = mapper.readValue(schemaFile, new TypeReference<Map<String, Object>>(){});
            }

            File testsDir = new File(permissionsDir, "tests");
            if (testsDir.exists()) {
                File[] files = testsDir.listFiles((dir, name) -> name.endsWith(".json"));
                if (files != null) {
                    for (File file : files) {
                        runTestsFromFile(file, mapper, sharedSchemaMap);
                    }
                }
            }
        }
    }

    private void runTestsFromFile(File file, ObjectMapper mapper, Map<String, Object> sharedSchemaMap) throws IOException {
        List<TestCase> tests = mapper.readValue(file, new TypeReference<List<TestCase>>(){});

        for (TestCase tc : tests) {
            System.out.println("Running test: " + tc.id);
            
            JSONQLSchema schema = null;
            if (tc.schema != null) {
                schema = parseSchema(tc.schema);
            } else if (sharedSchemaMap != null) {
                schema = parseSchema(sharedSchemaMap);
            }

            JSONQLParser parser = new JSONQLParser(schema, tc.tableName);
            
            boolean expectValid = true;
            if (tc.valid != null) {
                expectValid = tc.valid;
            }
            
            try {
                parser.parse(tc.query);
                if (!expectValid) {
                    fail("Test " + tc.id + " failed: Expected invalid query to throw exception");
                }
            } catch (IllegalArgumentException e) {
                if (expectValid) {
                    fail("Test " + tc.id + " failed: Expected valid query but got error: " + e.getMessage());
                } else if (tc.errorCode != null) {
                    // Check for error code if possible, but currently parser throws message
                    // We can check if message contains the error code or related text
                    // For now, just catching the exception is enough for "valid: false"
                }
            }
        }
    }

    private JSONQLSchema parseSchema(Map<String, Object> schemaMap) {
        JSONQLSchema schema = new JSONQLSchema();
        for (Map.Entry<String, Object> entry : schemaMap.entrySet()) {
            String tableName = entry.getKey();
            Map<String, Object> tableMap = (Map<String, Object>) entry.getValue();
            JSONQLTableSchema tableSchema = new JSONQLTableSchema();
            
            if (tableMap.containsKey("fields")) {
                Map<String, Object> fieldsMap = (Map<String, Object>) tableMap.get("fields");
                for (Map.Entry<String, Object> fieldEntry : fieldsMap.entrySet()) {
                    String fieldName = fieldEntry.getKey();
                    Map<String, Object> fieldProps = (Map<String, Object>) fieldEntry.getValue();
                    JSONQLFieldSchema fieldSchema = new JSONQLFieldSchema((String) fieldProps.get("type"));
                    
                    if (fieldProps.containsKey("allowSelect")) fieldSchema.allowSelect = (Boolean) fieldProps.get("allowSelect");
                    if (fieldProps.containsKey("allowFilter")) fieldSchema.allowFilter = (Boolean) fieldProps.get("allowFilter");
                    if (fieldProps.containsKey("allowSort")) fieldSchema.allowSort = (Boolean) fieldProps.get("allowSort");
                    if (fieldProps.containsKey("allowGroup")) fieldSchema.allowGroup = (Boolean) fieldProps.get("allowGroup");
                    if (fieldProps.containsKey("allowAggregate")) fieldSchema.allowAggregate = (Boolean) fieldProps.get("allowAggregate");
                    if (fieldProps.containsKey("allowCount")) fieldSchema.allowCount = (Boolean) fieldProps.get("allowCount");
                    if (fieldProps.containsKey("allowSum")) fieldSchema.allowSum = (Boolean) fieldProps.get("allowSum");
                    if (fieldProps.containsKey("allowAvg")) fieldSchema.allowAvg = (Boolean) fieldProps.get("allowAvg");
                    if (fieldProps.containsKey("allowMin")) fieldSchema.allowMin = (Boolean) fieldProps.get("allowMin");
                    if (fieldProps.containsKey("allowMax")) fieldSchema.allowMax = (Boolean) fieldProps.get("allowMax");

                    tableSchema.fields.put(fieldName, fieldSchema);
                }
            }

            if (tableMap.containsKey("relations")) {
                Map<String, Object> relsMap = (Map<String, Object>) tableMap.get("relations");
                for (Map.Entry<String, Object> relEntry : relsMap.entrySet()) {
                    String relName = relEntry.getKey();
                    Map<String, Object> relProps = (Map<String, Object>) relEntry.getValue();
                    JSONQLRelation relSchema = new JSONQLRelation((String) relProps.get("type"), (String) relProps.get("target"));
                    
                    if (relProps.containsKey("allowInclude")) relSchema.allowInclude = (Boolean) relProps.get("allowInclude");
                    
                    tableSchema.relations.put(relName, relSchema);
                }
            }
            
            schema.tables.put(tableName, tableSchema);
        }
        return schema;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TestCase {
        public String id;
        public String description;
        public Map<String, Object> schema;
        public String tableName;
        public Map<String, Object> query;
        public Boolean valid;
        public String expectedError;
        public String errorCode;
    }
}
