package org.jsonql.dialect;

public interface SQLDialect {
    String getName();
    String getPlaceholder(int index);
    String quoteIdentifier(String identifier);
    String getLimitOffset(int limit, int offset);
    boolean supportsReturning();
}
