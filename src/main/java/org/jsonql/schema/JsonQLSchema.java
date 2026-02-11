package org.jsonql.schema;

import java.util.Map;
import java.util.HashMap;

public class JsonQLSchema {
    public Map<String, JsonQLTableSchema> tables = new HashMap<>();

    public JsonQLSchema() {}

    public JsonQLSchema(Map<String, Object> schemaMap) {
        if (schemaMap != null && schemaMap.containsKey("tables")) {
            Map<String, Object> tablesMap = (Map<String, Object>) schemaMap.get("tables");
            for (Map.Entry<String, Object> entry : tablesMap.entrySet()) {
                this.tables.put(entry.getKey(), new JsonQLTableSchema((Map<String, Object>) entry.getValue()));
            }
        }
    }
}
