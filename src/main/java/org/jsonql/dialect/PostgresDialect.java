package org.jsonql.dialect;

public class PostgresDialect implements SQLDialect {
    @Override
    public String getPlaceholder(int index) {
        return "$" + index;
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + identifier + "\"";
    }

    @Override
    public String getLimitOffset(int limit, int offset) {
        return "LIMIT " + limit + " OFFSET " + offset;
    }
}
