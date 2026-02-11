package org.jsonql.schema;

import java.util.Map;
import java.util.HashMap;

public class JSONQLTableSchema {
    public Map<String, JSONQLFieldSchema> fields = new HashMap<>();
    public Map<String, JSONQLRelation> relations = new HashMap<>();

    public JSONQLTableSchema() {}

    @SuppressWarnings("unchecked")
    public JSONQLTableSchema(Map<String, Object> map) {
        if (map.containsKey("fields")) {
            Map<String, Object> fieldsMap = (Map<String, Object>) map.get("fields");
            for (Map.Entry<String, Object> entry : fieldsMap.entrySet()) {
                this.fields.put(entry.getKey(), new JSONQLFieldSchema((Map<String, Object>) entry.getValue()));
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
                this.relations.put(entry.getKey(), new JSONQLRelation((Map<String, Object>) entry.getValue()));
            }
        }
    }
}
