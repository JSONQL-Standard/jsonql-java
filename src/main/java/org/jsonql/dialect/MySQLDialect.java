package org.jsonql.dialect;

public class MySQLDialect implements SQLDialect {
    @Override
    public String getName() {
        return "mysql";
    }

    @Override
    public String getPlaceholder(int index) {
        return "?";
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "`" + identifier + "`";
    }

    @Override
    public String getLimitOffset(int limit, int offset) {
        if (limit == 0 && offset == 0) {
            return "LIMIT 0";
        }
        StringBuilder sb = new StringBuilder();
        if (limit > 0) {
            sb.append("LIMIT ").append(limit);
        } else if (offset > 0) {
            // MySQL requires LIMIT before OFFSET; use large number for unlimited
            sb.append("LIMIT 18446744073709551615");
        }
        if (offset > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("OFFSET ").append(offset);
        }
        return sb.toString();
    }

    @Override
    public boolean supportsReturning() {
        return false;
    }
}
