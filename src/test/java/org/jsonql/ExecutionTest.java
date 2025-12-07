package org.jsonql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsonql.dialect.GenericDialect;
import org.jsonql.hydrator.ResultHydrator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class ExecutionTest {

    private Connection connection;
    private ObjectMapper mapper = new ObjectMapper();

    @Before
    public void setUp() throws Exception {
        // 1. Load Data
        String specPathEnv = System.getenv("JSONQL_SPEC_PATH");
        File suitesDir;
        if (specPathEnv != null && !specPathEnv.isEmpty()) {
            suitesDir = new File(specPathEnv, "tests/suites");
        } else {
            suitesDir = new File("../jsonql-spec/tests/suites");
        }
        
        File dataFile = new File(suitesDir, "standard/data.json");
        if (!dataFile.exists()) {
            System.out.println("Data file not found, skipping setup");
            return;
        }

        Map<String, List<Map<String, Object>>> dataset = mapper.readValue(dataFile, 
            new TypeReference<Map<String, List<Map<String, Object>>>>(){});

        // 2. Setup DB
        connection = DriverManager.getConnection("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");

        // 3. Create Tables and Insert Data
        for (Map.Entry<String, List<Map<String, Object>>> entry : dataset.entrySet()) {
            String tableName = entry.getKey();
            List<Map<String, Object>> rows = entry.getValue();
            if (rows.isEmpty()) continue;

            // Infer schema
            Map<String, Object> firstRow = rows.get(0);
            List<String> colDefs = new ArrayList<>();
            List<String> colNames = new ArrayList<>();

            for (Map.Entry<String, Object> col : firstRow.entrySet()) {
                String colType = "VARCHAR(255)";
                Object val = col.getValue();
                if (val instanceof Integer) colType = "INT";
                else if (val instanceof Double) colType = "DOUBLE";
                else if (val instanceof Boolean) colType = "BOOLEAN";
                else if (val instanceof Map || val instanceof List) colType = "TEXT"; // Use TEXT for JSON
                
                colDefs.add(col.getKey() + " " + colType);
                colNames.add(col.getKey());
            }

            String createSQL = "CREATE TABLE " + tableName + " (" + String.join(", ", colDefs) + ")";
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createSQL);
            }

            // Insert data
            String placeholders = String.join(", ", Collections.nCopies(colNames.size(), "?"));
            String insertSQL = "INSERT INTO " + tableName + " (" + String.join(", ", colNames) + ") VALUES (" + placeholders + ")";

            try (PreparedStatement pstmt = connection.prepareStatement(insertSQL)) {
                for (Map<String, Object> row : rows) {
                    for (int i = 0; i < colNames.size(); i++) {
                        Object val = row.get(colNames.get(i));
                        if (val instanceof Map || val instanceof List) {
                            val = mapper.writeValueAsString(val);
                        }
                        pstmt.setObject(i + 1, val);
                    }
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
        }
    }

    @After
    public void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    public void testExecution() throws Exception {
        if (connection == null) return; // Skip if setup failed (e.g. no data file)

        String specPathEnv = System.getenv("JSONQL_SPEC_PATH");
        File suitesDir;
        if (specPathEnv != null && !specPathEnv.isEmpty()) {
            suitesDir = new File(specPathEnv, "tests/suites");
        } else {
            suitesDir = new File("../jsonql-spec/tests/suites");
        }

        File execFile = new File(suitesDir, "standard/tests/execution.json");
        if (!execFile.exists()) {
            fail("Execution tests not found");
        }

        List<ExecutionTestCase> tests = mapper.readValue(execFile, new TypeReference<List<ExecutionTestCase>>(){});
        SQLTranspiler transpiler = new SQLTranspiler(new GenericDialect());

        for (ExecutionTestCase tc : tests) {
            System.out.println("Running execution test: " + tc.id);
            
            // Transpile
            SQLTranspiler.TranspilationResult result = transpiler.transpile(tc.query, tc.tableName);
            
            // Execute
            try (PreparedStatement pstmt = connection.prepareStatement(result.sql)) {
                for (int i = 0; i < result.parameters.size(); i++) {
                    pstmt.setObject(i + 1, result.parameters.get(i));
                }
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    ResultHydrator hydrator = new ResultHydrator();
                    List<Map<String, Object>> rawResults = hydrator.hydrate(rs);
                    
                    // Normalize keys to lowercase for test comparison (H2 returns uppercase)
                    List<Map<String, Object>> actualResults = rawResults.stream().map(row -> {
                        Map<String, Object> newRow = new HashMap<>();
                        for (Map.Entry<String, Object> e : row.entrySet()) {
                            newRow.put(e.getKey().toLowerCase(), e.getValue());
                        }
                        return newRow;
                    }).collect(Collectors.toList());
                    
                    // Compare
                    assertEquals("Test " + tc.id + " row count mismatch", tc.expectedResult.size(), actualResults.size());
                    
                    for (int i = 0; i < tc.expectedResult.size(); i++) {
                        Map<String, Object> expected = tc.expectedResult.get(i);
                        Map<String, Object> actual = actualResults.get(i);
                        
                        for (Map.Entry<String, Object> entry : expected.entrySet()) {
                            assertTrue("Row " + i + " missing key " + entry.getKey(), actual.containsKey(entry.getKey()));
                            Object expVal = entry.getValue();
                            Object actVal = actual.get(entry.getKey());
                            
                            // Simple string comparison for now to handle type diffs (e.g. Integer vs Long)
                            assertEquals("Row " + i + " key " + entry.getKey() + " mismatch", 
                                String.valueOf(expVal), String.valueOf(actVal));
                        }
                    }
                }
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ExecutionTestCase {
        public String id;
        public String description;
        public String tableName;
        public Map<String, Object> query;
        public List<Map<String, Object>> expectedResult;
    }
}
