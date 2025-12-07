package org.jsonql;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.jsonql.dialect.GenericDialect;

public class TranspilationTest {

    @Test
    public void testTranspilation() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        File file = new File("../jsonql-spec/tests/transpilation/sql.json");
        
        if (!file.exists()) {
            fail("Transpilation test file not found at: " + file.getAbsolutePath());
        }

        List<TranspilationTestCase> tests = mapper.readValue(file, new TypeReference<List<TranspilationTestCase>>(){});
        SQLTranspiler transpiler = new SQLTranspiler(new GenericDialect());

        for (TranspilationTestCase tc : tests) {
            System.out.println("Running transpilation test: " + tc.id);
            SQLTranspiler.TranspilationResult result = transpiler.transpile(tc.query, tc.tableName);
            assertEquals("Test " + tc.id + " failed", tc.expectedSQL, result.sql);
            
            if (tc.expectedArgs != null) {
                assertEquals("Test " + tc.id + " args count mismatch", tc.expectedArgs.size(), result.parameters.size());
                for (int i = 0; i < tc.expectedArgs.size(); i++) {
                    assertEquals("Test " + tc.id + " arg " + i + " mismatch", tc.expectedArgs.get(i), result.parameters.get(i));
                }
            }
        }
    }

    static class TranspilationTestCase {
        public String id;
        public String description;
        public String tableName;
        public Map<String, Object> query;
        public String expectedSQL;
        public List<Object> expectedArgs;
    }
}
