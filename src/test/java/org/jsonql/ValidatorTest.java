package org.jsonql;

import org.jsonql.schema.JsonQLSchema;
import org.jsonql.schema.JsonQLTableSchema;
import org.jsonql.schema.JsonQLFieldSchema;
import org.jsonql.schema.JsonQLRelation;
import org.jsonql.validator.JsonQLValidator;
import org.jsonql.validator.JsonQLValidator.ValidationResult;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Unit tests for JsonQLValidator — covers field permissions,
 * aggregate validation, relation validation, and unknown field rejection.
 */
public class ValidatorTest {

    private JsonQLSchema schema;

    @Before
    public void setUp() {
        schema = new JsonQLSchema();
        JsonQLTableSchema users = new JsonQLTableSchema();

        // id: full access
        JsonQLFieldSchema idField = new JsonQLFieldSchema("integer");
        users.fields.put("id", idField);

        // name: full access
        JsonQLFieldSchema nameField = new JsonQLFieldSchema("string");
        users.fields.put("name", nameField);

        // email: selectable only, not filterable or sortable
        JsonQLFieldSchema emailField = new JsonQLFieldSchema("string");
        emailField.allowSelect = true;
        emailField.allowFilter = false;
        emailField.allowSort = false;
        users.fields.put("email", emailField);

        // age: allow filter/sort/aggregate
        JsonQLFieldSchema ageField = new JsonQLFieldSchema("integer");
        ageField.allowSelect = true;
        ageField.allowFilter = true;
        ageField.allowSort = true;
        ageField.allowAggregate = true;
        users.fields.put("age", ageField);

        // password: not selectable, not filterable
        JsonQLFieldSchema passwordField = new JsonQLFieldSchema("string");
        passwordField.allowSelect = false;
        passwordField.allowFilter = false;
        passwordField.allowSort = false;
        users.fields.put("password", passwordField);

        // status: allow groupBy
        JsonQLFieldSchema statusField = new JsonQLFieldSchema("string");
        statusField.allowGroup = true;
        users.fields.put("status", statusField);

        // Relation: orders (allowed to include)
        JsonQLRelation ordersRel = new JsonQLRelation("hasMany", "orders");
        ordersRel.foreignKey = "user_id";
        ordersRel.allowInclude = true;
        users.relations.put("orders", ordersRel);

        // Relation: sessions (NOT allowed to include)
        JsonQLRelation sessionsRel = new JsonQLRelation("hasMany", "sessions");
        sessionsRel.foreignKey = "user_id";
        sessionsRel.allowInclude = false;
        users.relations.put("sessions", sessionsRel);

        schema.tables.put("users", users);
    }

    // ---------- Field select permissions ----------

    @Test
    public void testValidFieldsAllowed() {
        JsonQLValidator v = new JsonQLValidator(schema, "users");
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("fields", Arrays.asList("id", "name", "email"));
        ValidationResult result = v.validate(query);
        assertTrue("Expected valid", result.valid);
    }

    @Test
    public void testSelectBlockedField() {
        JsonQLValidator v = new JsonQLValidator(schema, "users");
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("fields", Arrays.asList("id", "password"));
        ValidationResult result = v.validate(query);
        assertFalse("Expected invalid — password not selectable", result.valid);
    }

    @Test
    public void testEmptyFieldsArray() {
        JsonQLValidator v = new JsonQLValidator(schema, "users");
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("fields", Collections.emptyList());
        ValidationResult result = v.validate(query);
        assertFalse("Expected invalid — empty fields", result.valid);
    }

    // ---------- Where / filter permissions ----------

    @Test
    public void testFilterAllowed() {
        JsonQLValidator v = new JsonQLValidator(schema, "users");
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("where", Collections.singletonMap("age", Collections.singletonMap("gt", 18)));
        ValidationResult result = v.validate(query);
        assertTrue("Expected valid filter on age", result.valid);
    }

    @Test
    public void testFilterBlocked() {
        JsonQLValidator v = new JsonQLValidator(schema, "users");
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("where", Collections.singletonMap("email", Collections.singletonMap("eq", "a@b.com")));
        ValidationResult result = v.validate(query);
        assertFalse("Expected invalid — email not filterable", result.valid);
    }

    // ---------- Sort permissions ----------

    @Test
    public void testSortAllowed() {
        JsonQLValidator v = new JsonQLValidator(schema, "users");
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("sort", Arrays.asList("age", "-name"));
        ValidationResult result = v.validate(query);
        assertTrue("Expected valid sort", result.valid);
    }

    @Test
    public void testSortBlocked() {
        JsonQLValidator v = new JsonQLValidator(schema, "users");
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("sort", "email");
        ValidationResult result = v.validate(query);
        assertFalse("Expected invalid — email not sortable", result.valid);
    }

    // ---------- GroupBy ----------

    @Test
    public void testGroupByAllowed() {
        JsonQLValidator v = new JsonQLValidator(schema, "users");
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("groupBy", Arrays.asList("status"));
        ValidationResult result = v.validate(query);
        assertTrue("Expected valid groupBy on status", result.valid);
    }

    // ---------- Aggregate ----------

    @Test
    public void testAggregateAllowed() {
        JsonQLValidator v = new JsonQLValidator(schema, "users");
        Map<String, Object> query = new LinkedHashMap<>();
        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put("avg_age", Collections.singletonMap("avg", "age"));
        query.put("aggregate", agg);
        ValidationResult result = v.validate(query);
        assertTrue("Expected valid aggregate on age", result.valid);
    }

    @Test
    public void testAggregateUnknownFunction() {
        JsonQLValidator v = new JsonQLValidator(schema, "users");
        Map<String, Object> query = new LinkedHashMap<>();
        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put("result", Collections.singletonMap("median", "age"));
        query.put("aggregate", agg);
        ValidationResult result = v.validate(query);
        assertFalse("Expected invalid — unknown aggregate function", result.valid);
    }

    // ---------- Include / relations ----------

    @Test
    public void testIncludeAllowed() {
        JsonQLValidator v = new JsonQLValidator(schema, "users");
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("include", Arrays.asList("orders"));
        ValidationResult result = v.validate(query);
        assertTrue("Expected valid include orders", result.valid);
    }

    @Test
    public void testIncludeBlocked() {
        JsonQLValidator v = new JsonQLValidator(schema, "users");
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("include", Arrays.asList("sessions"));
        ValidationResult result = v.validate(query);
        assertFalse("Expected invalid — sessions not includable", result.valid);
    }

    // ---------- Unknown table => pass (no schema rules) ----------

    @Test
    public void testUnknownTablePassesValidation() {
        JsonQLValidator v = new JsonQLValidator(schema, "products");
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("fields", Arrays.asList("id"));
        ValidationResult result = v.validate(query);
        assertTrue("Unknown table should pass", result.valid);
    }

    // ---------- rejectUnknownFields ----------

    @Test
    public void testRejectUnknownFieldsMode() {
        JsonQLValidator v = new JsonQLValidator(schema, "users", true);
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("fields", Arrays.asList("id", "nonexistent_field"));
        ValidationResult result = v.validate(query);
        assertFalse("Expected invalid — unknown field with strict mode", result.valid);
    }

    @Test
    public void testAllowUnknownFieldsByDefault() {
        JsonQLValidator v = new JsonQLValidator(schema, "users");
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("fields", Arrays.asList("id", "nonexistent_field"));
        ValidationResult result = v.validate(query);
        assertTrue("Unknown field should pass in permissive mode", result.valid);
    }

    // ---------- validateOrThrow ----------

    @Test(expected = JsonQLValidationException.class)
    public void testValidateOrThrowOnInvalid() {
        JsonQLValidator v = new JsonQLValidator(schema, "users");
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("fields", Arrays.asList("password"));
        v.validateOrThrow(query);
    }

    @Test
    public void testValidateOrThrowOnValid() {
        JsonQLValidator v = new JsonQLValidator(schema, "users");
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("fields", Arrays.asList("id", "name"));
        v.validateOrThrow(query);
    }
}
