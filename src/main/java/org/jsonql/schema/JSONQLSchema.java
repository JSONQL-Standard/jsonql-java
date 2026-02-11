package org.jsonql.schema;

import java.util.Map;
import java.util.HashMap;

public class JSONQLSchema {
    public Map<String, JSONQLTableSchema> tables = new HashMap<>();

    public JSONQLSchema() {}

    public JSONQLSchema(Map<String, Object> schemaMap) {
        if (schemaMap != null && schemaMap.containsKey("tables")) {
            Map<String, Object> tablesMap = (Map<String, Object>) schemaMap.get("tables");
            for (Map.Entry<String, Object> entry : tablesMap.entrySet()) {
                this.tables.put(entry.getKey(), new JSONQLTableSchema((Map<String, Object>) entry.getValue()));
            }
        }
    }
}
