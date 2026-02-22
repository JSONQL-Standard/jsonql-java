package org.jsonql.dialect;

public class GenericDialect implements SQLDialect {
    @Override
    public String getName() {
        return "generic";
    }

    @Override
    public String getPlaceholder(int index) {
        return "?";
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return identifier;
    }

    @Override
    public String getLimitOffset(int limit, int offset) {
        return "LIMIT " + limit + " OFFSET " + offset;
    }

    @Override
    public boolean supportsReturning() {
        return false;
    }
}
