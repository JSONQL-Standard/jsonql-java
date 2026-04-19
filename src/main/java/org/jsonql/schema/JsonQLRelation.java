package org.jsonql.schema;

import java.util.Map;

public class JsonQLRelation {
    public String type;
    public String target;
    public String foreignKey;
    public Boolean allowInclude = true;

    public JsonQLRelation() {}

    public JsonQLRelation(String type, String target) {
        this.type = type;
        this.target = target;
    }

    public JsonQLRelation(Map<String, Object> map) {
        if (map.containsKey("type")) this.type = (String) map.get("type");
        if (map.containsKey("target")) this.target = (String) map.get("target");
        if (map.containsKey("foreignKey")) this.foreignKey = (String) map.get("foreignKey");
        if (map.containsKey("allowInclude")) this.allowInclude = (Boolean) map.get("allowInclude");
    }
}
