package org.jsonql.dialect;

public class PostgresDialect implements SQLDialect {
    @Override
    public String getName() {
        return "postgres";
    }

    @Override
    public String getPlaceholder(int index) {
        return "?";
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + identifier + "\"";
    }

    @Override
    public String getLimitOffset(int limit, int offset) {
        if (limit == 0 && offset == 0) {
            return "LIMIT 0";
        }
        if (limit > 0) {
            return "LIMIT " + limit + (offset > 0 ? " OFFSET " + offset : "");
        }
        if (offset > 0) {
            return "OFFSET " + offset;
        }
        return "";
    }

    @Override
    public boolean supportsReturning() {
        return true;
    }
}
