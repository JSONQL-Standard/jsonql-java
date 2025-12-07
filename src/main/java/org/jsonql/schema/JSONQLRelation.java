package org.jsonql.schema;

public class JSONQLRelation {
    public String type;
    public String target;
    public String foreignKey;
    public Boolean allowInclude = true;

    public JSONQLRelation() {}

    public JSONQLRelation(String type, String target) {
        this.type = type;
        this.target = target;
    }
}
