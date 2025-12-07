package org.jsonql.schema;

import java.util.Map;
import java.util.HashMap;

public class JSONQLTableSchema {
    public Map<String, JSONQLFieldSchema> fields = new HashMap<>();
    public Map<String, JSONQLRelation> relations = new HashMap<>();
}
