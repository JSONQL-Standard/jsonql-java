package org.jsonql;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

/** Unit tests for the {@link JsonQLException} error code field and the exception hierarchy. */
public class ExceptionCodeTest {

    @Test
    public void testBaseExceptionDefaultCode() {
        JsonQLException ex = new JsonQLException("something went wrong");
        assertEquals("JSONQL_ERROR", ex.getCode());
        assertEquals("something went wrong", ex.getMessage());
    }

    @Test
    public void testBaseExceptionCustomCode() {
        JsonQLException ex = new JsonQLException("oops", "CUSTOM_CODE");
        assertEquals("CUSTOM_CODE", ex.getCode());
        assertEquals("oops", ex.getMessage());
    }

    @Test
    public void testBaseExceptionWithCauseDefaultCode() {
        RuntimeException cause = new RuntimeException("root");
        JsonQLException ex = new JsonQLException("wrapper", cause);
        assertEquals("JSONQL_ERROR", ex.getCode());
        assertSame(cause, ex.getCause());
    }

    @Test
    public void testBaseExceptionWithCauseCustomCode() {
        RuntimeException cause = new RuntimeException("root");
        JsonQLException ex = new JsonQLException("wrapper", "MY_CODE", cause);
        assertEquals("MY_CODE", ex.getCode());
        assertSame(cause, ex.getCause());
    }

    @Test
    public void testValidationExceptionCode() {
        List<org.jsonql.validator.JsonQLValidator.ValidationError> errors = Collections.emptyList();
        JsonQLValidationException ex = new JsonQLValidationException("bad query", errors);
        assertEquals("VALIDATION_ERROR", ex.getCode());
        assertNotNull(ex.getErrors());
    }

    @Test
    public void testTranspileExceptionCode() {
        JsonQLTranspileException ex = new JsonQLTranspileException("bad field");
        assertEquals("TRANSPILE_ERROR", ex.getCode());
    }

    @Test
    public void testTranspileExceptionWithCauseCode() {
        Exception cause = new Exception("root");
        JsonQLTranspileException ex = new JsonQLTranspileException("transpile failed", cause);
        assertEquals("TRANSPILE_ERROR", ex.getCode());
        assertSame(cause, ex.getCause());
    }

    @Test
    public void testExecutionExceptionCode() {
        JsonQLExecutionException ex = new JsonQLExecutionException("db error");
        assertEquals("EXECUTION_ERROR", ex.getCode());
    }

    @Test
    public void testExecutionExceptionWithCauseCode() {
        java.sql.SQLException cause = new java.sql.SQLException("connection lost");
        JsonQLExecutionException ex = new JsonQLExecutionException("exec failed", cause);
        assertEquals("EXECUTION_ERROR", ex.getCode());
        assertSame(cause, ex.getCause());
    }

    @Test
    public void testExceptionHierarchy() {
        JsonQLValidationException vex =
                new JsonQLValidationException("val", Collections.emptyList());
        JsonQLTranspileException tex = new JsonQLTranspileException("trans");
        JsonQLExecutionException eex = new JsonQLExecutionException("exec");

        assertTrue(vex instanceof JsonQLException);
        assertTrue(tex instanceof JsonQLException);
        assertTrue(eex instanceof JsonQLException);
        assertTrue(vex instanceof RuntimeException);
    }
}
