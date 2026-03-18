package org.jsonql.adapter;

import org.jsonql.JsonQLLifecycle;
import org.jsonql.JsonQLLogger;
import org.jsonql.cache.CacheProvider;
import org.jsonql.schema.JsonQLSchema;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Configuration for a JSONQL framework adapter.
 *
 * <p>Mirrors the adapter options pattern used in TS/Go/Python SDKs.</p>
 */
public class AdapterOptions {

    /** The SQL dialect name: "postgres", "mysql", "sqlite", "mssql". */
    public String dialect = "sqlite";

    /** Schema for validation (optional). */
    public JsonQLSchema schema;

    /** Supplier that provides a JDBC Connection per-request. */
    public Supplier<Connection> connectionSupplier;

    /** Lifecycle hook object (optional). */
    public JsonQLLifecycle lifecycle;

    /** Logger instance (optional, defaults to NoOp). */
    public JsonQLLogger logger;

    /** Enable debug logging. */
    public boolean debug = false;

    /**
     * Table whitelist. If non-null, only these tables are allowed.
     * Can be a list of table names or a mapping of URL-path → actual table name.
     */
    public List<String> allowedTables;

    /** Table alias mapping: URL path → actual table name. */
    public Map<String, String> tableMapping;

    /** Cache provider for query transpilation results (optional). */
    public CacheProvider cache;

    /** Cache TTL in seconds (default 60). */
    public int cacheTtl = 60;

    public AdapterOptions() {}

    // ── Builder-style setters ──────────────────────────────────────────

    public AdapterOptions dialect(String dialect) {
        this.dialect = dialect;
        return this;
    }

    public AdapterOptions schema(JsonQLSchema schema) {
        this.schema = schema;
        return this;
    }

    public AdapterOptions connectionSupplier(Supplier<Connection> supplier) {
        this.connectionSupplier = supplier;
        return this;
    }

    public AdapterOptions lifecycle(JsonQLLifecycle lifecycle) {
        this.lifecycle = lifecycle;
        return this;
    }

    public AdapterOptions logger(JsonQLLogger logger) {
        this.logger = logger;
        return this;
    }

    public AdapterOptions debug(boolean debug) {
        this.debug = debug;
        return this;
    }

    public AdapterOptions allowedTables(List<String> tables) {
        this.allowedTables = tables;
        return this;
    }

    public AdapterOptions tableMapping(Map<String, String> mapping) {
        this.tableMapping = mapping;
        return this;
    }

    public AdapterOptions cache(CacheProvider cache) {
        this.cache = cache;
        return this;
    }

    public AdapterOptions cacheTtl(int seconds) {
        this.cacheTtl = seconds;
        return this;
    }
}
