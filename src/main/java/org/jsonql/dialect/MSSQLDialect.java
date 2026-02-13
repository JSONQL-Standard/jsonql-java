package org.jsonql.dialect;

public class MSSQLDialect implements SQLDialect {
    @Override
    public String getPlaceholder(int index) {
        return "?";
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "[" + identifier + "]";
    }

    @Override
    public String getLimitOffset(int limit, int offset) {
        return "OFFSET " + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
    }
}
