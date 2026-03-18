package org.jsonql.dialect;

public class MSSQLDialect implements SQLDialect {
    @Override
    public String getName() {
        return "mssql";
    }

    @Override
    public String getPlaceholder(int index) {
        return "@p" + (index + 1);
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "[" + identifier + "]";
    }

    @Override
    public String getLimitOffset(int limit, int offset) {
        if (limit == 0 && offset == 0) {
            return "OFFSET 0 ROWS FETCH NEXT 0 ROWS ONLY";
        }
        if (limit > 0) {
            int off = Math.max(offset, 0);
            return "OFFSET " + off + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
        }
        if (offset > 0) {
            return "OFFSET " + offset + " ROWS";
        }
        return "";
    }

    @Override
    public boolean supportsReturning() {
        return false;
    }
}
