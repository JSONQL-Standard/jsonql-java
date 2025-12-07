package org.jsonql;

import org.jsonql.dialect.SQLDialect;
import org.jsonql.dialect.PostgresDialect;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class SQLTranspiler {

    private final SQLDialect dialect;

    public SQLTranspiler(SQLDialect dialect) {
        this.dialect = dialect;
    }

    // Default constructor for backward compatibility (defaults to Postgres)
    public SQLTranspiler() {
        this(new PostgresDialect());
    }

    public static class TranspilationResult {
        public String sql;
        public List<Object> parameters;

        public TranspilationResult(String sql, List<Object> parameters) {
            this.sql = sql;
            this.parameters = parameters;
        }
    }
    
    public TranspilationResult transpile(Map<String, Object> query, String tableName) {
        if (!isValidIdentifier(tableName)) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }

        List<Object> parameters = new ArrayList<>();

        // 1. SELECT clause
        String selectClause = "*";
        if (query.containsKey("fields")) {
            Object fields = query.get("fields");
            if (fields instanceof List) {
                List<?> fieldsList = (List<?>) fields;
                if (!fieldsList.isEmpty()) {
                    List<String> cols = new ArrayList<>();
                    for (Object f : fieldsList) {
                        String fieldStr = f.toString();
                        if (!isValidIdentifier(fieldStr)) {
                            throw new IllegalArgumentException("Invalid field name: " + fieldStr);
                        }
                        cols.add(dialect.quoteIdentifier(fieldStr));
                    }
                    selectClause = String.join(", ", cols);
                }
            }
        }

        // 2. FROM clause
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(selectClause).append(" FROM ").append(dialect.quoteIdentifier(tableName));

        // 3. WHERE clause
        if (query.containsKey("where")) {
            Object where = query.get("where");
            if (where instanceof Map) {
                Map<?, ?> whereMap = (Map<?, ?>) where;
                List<String> conditions = new ArrayList<>();
                
                for (Map.Entry<?, ?> entry : whereMap.entrySet()) {
                    String field = entry.getKey().toString();
                    if (!isValidIdentifier(field)) {
                        throw new IllegalArgumentException("Invalid field name in where clause: " + field);
                    }
                    String quotedField = dialect.quoteIdentifier(field);

                    Object cond = entry.getValue();
                    
                    if (cond instanceof Map) {
                        Map<?, ?> condMap = (Map<?, ?>) cond;
                        if (condMap.containsKey("eq")) {
                            Object val = condMap.get("eq");
                            conditions.add(quotedField + " = " + dialect.getPlaceholder(parameters.size() + 1));
                            parameters.add(val);
                        } else if (condMap.containsKey("neq")) {
                            Object val = condMap.get("neq");
                            conditions.add(quotedField + " != " + dialect.getPlaceholder(parameters.size() + 1));
                            parameters.add(val);
                        } else if (condMap.containsKey("gt")) {
                            Object val = condMap.get("gt");
                            conditions.add(quotedField + " > " + dialect.getPlaceholder(parameters.size() + 1));
                            parameters.add(val);
                        } else if (condMap.containsKey("gte")) {
                            Object val = condMap.get("gte");
                            conditions.add(quotedField + " >= " + dialect.getPlaceholder(parameters.size() + 1));
                            parameters.add(val);
                        } else if (condMap.containsKey("lt")) {
                            Object val = condMap.get("lt");
                            conditions.add(quotedField + " < " + dialect.getPlaceholder(parameters.size() + 1));
                            parameters.add(val);
                        } else if (condMap.containsKey("lte")) {
                            Object val = condMap.get("lte");
                            conditions.add(quotedField + " <= " + dialect.getPlaceholder(parameters.size() + 1));
                            parameters.add(val);
                        } else if (condMap.containsKey("like")) {
                            Object val = condMap.get("like");
                            conditions.add(quotedField + " LIKE " + dialect.getPlaceholder(parameters.size() + 1));
                            parameters.add(val);
                        } else if (condMap.containsKey("in")) {
                            Object val = condMap.get("in");
                            if (val instanceof List) {
                                List<?> list = (List<?>) val;
                                if (!list.isEmpty()) {
                                    List<String> placeholders = new ArrayList<>();
                                    for (Object o : list) {
                                        placeholders.add(dialect.getPlaceholder(parameters.size() + 1));
                                        parameters.add(o);
                                    }
                                    conditions.add(quotedField + " IN (" + String.join(", ", placeholders) + ")");
                                }
                            }
                        }
                    }
                }
                
                if (!conditions.isEmpty()) {
                    sql.append(" WHERE ").append(String.join(" AND ", conditions));
                }
            }
        }

        // 4. SORT clause
        if (query.containsKey("sort")) {
            Object sort = query.get("sort");
            List<String> sortFields = new ArrayList<>();
            
            if (sort instanceof String) {
                String s = (String) sort;
                boolean desc = s.startsWith("-");
                String field = desc ? s.substring(1) : s;
                if (!isValidIdentifier(field)) {
                    throw new IllegalArgumentException("Invalid sort field: " + field);
                }
                sortFields.add(dialect.quoteIdentifier(field) + (desc ? " DESC" : " ASC"));
            } else if (sort instanceof List) {
                for (Object o : (List<?>) sort) {
                    String s = o.toString();
                    boolean desc = s.startsWith("-");
                    String field = desc ? s.substring(1) : s;
                    if (!isValidIdentifier(field)) {
                        throw new IllegalArgumentException("Invalid sort field: " + field);
                    }
                    sortFields.add(dialect.quoteIdentifier(field) + (desc ? " DESC" : " ASC"));
                }
            }
            
            if (!sortFields.isEmpty()) {
                sql.append(" ORDER BY ").append(String.join(", ", sortFields));
            }
        }

        // 5. LIMIT/OFFSET
        int limit = -1;
        int offset = 0;
        
        if (query.containsKey("limit")) {
            Object l = query.get("limit");
            if (l instanceof Number) {
                limit = ((Number) l).intValue();
            }
        }
        
        if (query.containsKey("skip")) {
            Object s = query.get("skip");
            if (s instanceof Number) {
                offset = ((Number) s).intValue();
            }
        }
        
        if (limit != -1) {
            sql.append(" ").append(dialect.getLimitOffset(limit, offset));
        }

        return new TranspilationResult(sql.toString(), parameters);
    }

    private boolean isValidIdentifier(String id) {
        if (id == null || id.isEmpty()) return false;
        for (char c : id.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }
}
