package org.jsonql.schema;

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
}
