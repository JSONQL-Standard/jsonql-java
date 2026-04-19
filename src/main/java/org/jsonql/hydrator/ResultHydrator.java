package org.jsonql.hydrator;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

public class ResultHydrator {

    /**
     * Hydrates a ResultSet into a list of nested Maps. Assumes that columns for nested objects are
     * aliased with double underscores (e.g., "author__name").
     */
    public List<Map<String, Object>> hydrate(ResultSet rs) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        List<String> colNames = new ArrayList<>();
        for (int i = 1; i <= colCount; i++) {
            colNames.add(meta.getColumnLabel(i));
        }

        while (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            for (String col : colNames) {
                hydrateColumn(row, col, rs.getObject(col));
            }
            results.add(row);
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private void hydrateColumn(Map<String, Object> row, String colName, Object value) {
        // Normalize numeric types (BigDecimal -> Long/Double)
        if (value instanceof java.math.BigDecimal) {
            java.math.BigDecimal bd = (java.math.BigDecimal) value;
            try {
                if (bd.scale() <= 0 || bd.stripTrailingZeros().scale() <= 0) {
                    value = bd.longValueExact();
                } else {
                    value = bd.doubleValue();
                }
            } catch (Exception ignored) {
                // keep original value if normalization fails
            }
        }
        // Normalize whole-number doubles/floats to long (e.g., SQLite SUM returns 15.0 instead of
        // 15)
        if (value instanceof Double) {
            double d = (Double) value;
            if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) <= Long.MAX_VALUE) {
                value = (long) d;
            }
        } else if (value instanceof Float) {
            float f = (Float) value;
            if (f == Math.floor(f) && !Float.isInfinite(f) && Math.abs(f) <= Long.MAX_VALUE) {
                value = (long) f;
            }
        }

        if (colName.contains("__")) {
            // Handle nested property: "author__name" -> author: { name: "..." }
            String[] parts = colName.split("__", 2);
            String parentKey = parts[0];
            String childKey = parts[1];

            Map<String, Object> parent;
            if (row.containsKey(parentKey)) {
                Object obj = row.get(parentKey);
                if (obj instanceof Map) {
                    parent = (Map<String, Object>) obj;
                } else {
                    // Conflict: field exists but is not a map.
                    // For now, overwrite or ignore? Let's assume structure is consistent.
                    // If it's null, we can initialize it.
                    if (obj == null) {
                        parent = new HashMap<>();
                        row.put(parentKey, parent);
                    } else {
                        // This shouldn't happen in a well-formed query result
                        return;
                    }
                }
            } else {
                parent = new HashMap<>();
                row.put(parentKey, parent);
            }

            hydrateColumn(parent, childKey, value);
        } else {
            row.put(colName, value);
        }
    }
}
