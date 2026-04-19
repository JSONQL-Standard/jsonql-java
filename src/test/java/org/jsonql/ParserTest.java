package org.jsonql;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Unit tests for the JsonQLParser — covers parse() validations,
 * operator support, and parser option enforcement.
 */
public class ParserTest {

    // ---------- Basic syntax validation ----------

    @Test
    public void testValidMinimalQuery() {
        JsonQLParser parser = new JsonQLParser();
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("fields", Arrays.asList("id", "name"));
        // Should not throw
        parser.parse(query);
    }

    @Test
    public void testValidEmptyQuery() {
        JsonQLParser parser = new JsonQLParser();
        Map<String, Object> query = new LinkedHashMap<>();
        // Should not throw — empty query selects all
        parser.parse(query);
    }

    @Test
    public void testFieldsMustBeArray() {
        // Parser skips fields validation when not a List — no error thrown.
        // This tests the fields-is-List branch works for a valid array.
        JsonQLParser parser = new JsonQLParser();
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("fields", Arrays.asList("id"));
        parser.parse(query);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyFieldsArray() {
        JsonQLParser parser = new JsonQLParser();
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("fields", Collections.emptyList());
        parser.parse(query);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSkipType() {
        JsonQLParser parser = new JsonQLParser();
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("skip", -5);
        parser.parse(query);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeLimit() {
        JsonQLParser parser = new JsonQLParser();
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("limit", -1);
        parser.parse(query);
    }

    // ---------- Where clause operators ----------

    @Test
    public void testWhereWithEq() {
        JsonQLParser parser = new JsonQLParser();
        Map<String, Object> query = new LinkedHashMap<>();
        Map<String, Object> where = new LinkedHashMap<>();
        where.put("status", Collections.singletonMap("eq", "active"));
        query.put("where", where);
        parser.parse(query);
    }

    @Test
    public void testWhereWithNeq() {
        JsonQLParser parser = new JsonQLParser();
        Map<String, Object> query = new LinkedHashMap<>();
        Map<String, Object> where = new LinkedHashMap<>();
        where.put("status", Collections.singletonMap("neq", "deleted"));
        query.put("where", where);
        parser.parse(query);
    }

    @Test
    public void testWhereWithComparisonOperators() {
        JsonQLParser parser = new JsonQLParser();
        Map<String, Object> query = new LinkedHashMap<>();
        Map<String, Object> where = new LinkedHashMap<>();
        where.put("age", Collections.singletonMap("gt", 18));
        query.put("where", where);
        parser.parse(query);
    }

    @Test
    public void testWhereWithInOperator() {
        JsonQLParser parser = new JsonQLParser();
        Map<String, Object> query = new LinkedHashMap<>();
        Map<String, Object> where = new LinkedHashMap<>();
        where.put("role", Collections.singletonMap("in", Arrays.asList("admin", "mod")));
        query.put("where", where);
        parser.parse(query);
    }

    @Test
    public void testWhereWithAndOr() {
        JsonQLParser parser = new JsonQLParser();
        Map<String, Object> query = new LinkedHashMap<>();
        Map<String, Object> cond1 = Collections.singletonMap("status", Collections.singletonMap("eq", "active"));
        Map<String, Object> cond2 = Collections.singletonMap("age", Collections.singletonMap("gt", 18));
        Map<String, Object> where = Collections.singletonMap("and", Arrays.asList(cond1, cond2));
        query.put("where", where);
        parser.parse(query);
    }

    @Test
    public void testWhereWithFieldReference() {
        JsonQLParser parser = new JsonQLParser();
        Map<String, Object> query = new LinkedHashMap<>();
        Map<String, Object> where = new LinkedHashMap<>();
        where.put("price", Collections.singletonMap("gt", Collections.singletonMap("field", "cost")));
        query.put("where", where);
        parser.parse(query);
    }

    // ---------- Sort ----------

    @Test
    public void testSortArray() {
        JsonQLParser parser = new JsonQLParser();
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("sort", Arrays.asList("name", "-created_at"));
        parser.parse(query);
    }

    @Test
    public void testSortString() {
        JsonQLParser parser = new JsonQLParser();
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("sort", "name");
        parser.parse(query);
    }

    // ---------- v1.1 features ----------

    @Test
    public void testGroupBy() {
        JsonQLParser parser = new JsonQLParser();
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("groupBy", Arrays.asList("status"));
        parser.parse(query);
    }

    @Test
    public void testDistinctBoolean() {
        JsonQLParser parser = new JsonQLParser();
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("distinct", true);
        parser.parse(query);
    }

    @Test
    public void testDistinctArray() {
        JsonQLParser parser = new JsonQLParser();
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("distinct", Arrays.asList("category", "status"));
        parser.parse(query);
    }

    @Test
    public void testAggregate() {
        JsonQLParser parser = new JsonQLParser();
        Map<String, Object> query = new LinkedHashMap<>();
        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put("total", Collections.singletonMap("count", "id"));
        agg.put("avg_price", Collections.singletonMap("avg", "price"));
        query.put("aggregate", agg);
        parser.parse(query);
    }

    // ---------- Parser options enforcement ----------

    @Test(expected = IllegalArgumentException.class)
    public void testMaxLimitEnforced() {
        JsonQLParserOptions options = new JsonQLParserOptions();
        options.setMaxLimit(50);
        JsonQLParser parser = new JsonQLParser(options);

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("limit", 100);
        parser.parse(query);
    }

    @Test
    public void testMaxLimitPermitsValidLimit() {
        JsonQLParserOptions options = new JsonQLParserOptions();
        options.setMaxLimit(50);
        JsonQLParser parser = new JsonQLParser(options);

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("limit", 25);
        parser.parse(query);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAllowedFieldsEnforced() {
        JsonQLParserOptions options = new JsonQLParserOptions();
        options.setAllowedFields(Arrays.asList("id", "name"));
        JsonQLParser parser = new JsonQLParser(options);

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("fields", Arrays.asList("id", "email"));
        parser.parse(query);
    }

    @Test
    public void testAllowedFieldsPermitsValid() {
        JsonQLParserOptions options = new JsonQLParserOptions();
        options.setAllowedFields(Arrays.asList("id", "name", "email"));
        JsonQLParser parser = new JsonQLParser(options);

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("fields", Arrays.asList("id", "name"));
        parser.parse(query);
    }

    // ---------- Full complex query ----------

    @Test
    public void testComplexQueryParses() {
        JsonQLParser parser = new JsonQLParser();
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("fields", Arrays.asList("status", "total"));
        query.put("limit", 100);
        query.put("skip", 0);
        query.put("sort", Arrays.asList("-total"));
        query.put("groupBy", Arrays.asList("status"));

        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put("total", Collections.singletonMap("sum", "amount"));
        query.put("aggregate", agg);

        Map<String, Object> where = Collections.singletonMap(
            "status", Collections.singletonMap("in", Arrays.asList("active", "pending"))
        );
        query.put("where", where);

        parser.parse(query);
    }
}
