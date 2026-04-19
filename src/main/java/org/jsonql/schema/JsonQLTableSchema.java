package org.jsonql.schema;

import java.util.HashMap;
import java.util.Map;

public class JsonQLTableSchema {
    public Map<String, JsonQLFieldSchema> fields = new HashMap<>();
    public Map<String, JsonQLRelation> relations = new HashMap<>();
    public String primaryKey = "id";

    public JsonQLTableSchema() {}

    @SuppressWarnings("unchecked")
    public JsonQLTableSchema(Map<String, Object> map) {
        if (map.containsKey("fields")) {
            Map<String, Object> fieldsMap = (Map<String, Object>) map.get("fields");
            for (Map.Entry<String, Object> entry : fieldsMap.entrySet()) {
                this.fields.put(
                        entry.getKey(),
                        new JsonQLFieldSchema((Map<String, Object>) entry.getValue()));
            }
        }
        // Support both "relations" (standard) and "relationships" (legacy)
        Map<String, Object> relsMap = null;
        if (map.containsKey("relations")) {
            relsMap = (Map<String, Object>) map.get("relations");
        } else if (map.containsKey("relationships")) {
            relsMap = (Map<String, Object>) map.get("relationships");
        }
        if (relsMap != null) {
            for (Map.Entry<String, Object> entry : relsMap.entrySet()) {
                this.relations.put(
                        entry.getKey(), new JsonQLRelation((Map<String, Object>) entry.getValue()));
            }
        }
        if (map.containsKey("primaryKey")) {
            Object pk = map.get("primaryKey");
            if (pk instanceof String) {
                this.primaryKey = (String) pk;
            }
        }
    }
}
