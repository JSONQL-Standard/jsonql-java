package org.jsonql.schema;

import java.util.Map;

public class JSONQLFieldSchema {
    public String type;
    public Boolean allowSelect = true;
    public Boolean allowFilter = true;
    public Boolean allowSort = true;
    public Boolean allowGroup = true;
    public Boolean allowAggregate = true;
    public Boolean allowCount;
    public Boolean allowSum;
    public Boolean allowAvg;
    public Boolean allowMin;
    public Boolean allowMax;

    public JSONQLFieldSchema() {}

    public JSONQLFieldSchema(String type) {
        this.type = type;
    }

    public JSONQLFieldSchema(Map<String, Object> map) {
        if (map.containsKey("type")) this.type = (String) map.get("type");
        if (map.containsKey("allowSelect")) this.allowSelect = (Boolean) map.get("allowSelect");
        if (map.containsKey("allowFilter")) this.allowFilter = (Boolean) map.get("allowFilter");
        if (map.containsKey("allowSort")) this.allowSort = (Boolean) map.get("allowSort");
        if (map.containsKey("allowGroup")) this.allowGroup = (Boolean) map.get("allowGroup");
        if (map.containsKey("allowAggregate")) this.allowAggregate = (Boolean) map.get("allowAggregate");
        if (map.containsKey("allowCount")) this.allowCount = (Boolean) map.get("allowCount");
        if (map.containsKey("allowSum")) this.allowSum = (Boolean) map.get("allowSum");
        if (map.containsKey("allowAvg")) this.allowAvg = (Boolean) map.get("allowAvg");
        if (map.containsKey("allowMin")) this.allowMin = (Boolean) map.get("allowMin");
        if (map.containsKey("allowMax")) this.allowMax = (Boolean) map.get("allowMax");
    }
}
