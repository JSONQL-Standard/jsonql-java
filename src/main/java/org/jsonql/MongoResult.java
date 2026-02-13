package org.jsonql;

import java.util.*;

/**
 * Result of transpiling a JSONQL query into MongoDB operations.
 */
public class MongoResult {
    public String collection;
    public String operation; // "find", "insertOne", "insertMany", "updateMany", "deleteMany", "aggregate"
    public Map<String, Object> filter;
    public Map<String, Object> projection;
    public Map<String, Object> sort;
    public int limit = -1;
    public int skip = -1;
    public List<Map<String, Object>> pipeline;
    public Map<String, Object> document;
    public List<Map<String, Object>> documents;
    public Map<String, Object> update;

    public MongoResult(String collection, String operation) {
        this.collection = collection;
        this.operation = operation;
        this.filter = new LinkedHashMap<>();
    }
}
