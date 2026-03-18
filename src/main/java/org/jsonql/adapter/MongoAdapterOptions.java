package org.jsonql.adapter;

import org.jsonql.JsonQLLogger;
import org.jsonql.MongoDriverInterface;

/**
 * Configuration for the MongoDB JSONQL adapter.
 */
public class MongoAdapterOptions {
    public MongoDriverInterface driver;
    public JsonQLLogger logger;
    public boolean debug;

    public MongoAdapterOptions driver(MongoDriverInterface driver) {
        this.driver = driver;
        return this;
    }

    public MongoAdapterOptions logger(JsonQLLogger logger) {
        this.logger = logger;
        return this;
    }

    public MongoAdapterOptions debug(boolean debug) {
        this.debug = debug;
        return this;
    }
}
