package org.jsonql;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;

import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.*;

/**
 * Concrete MongoDB driver implementation using the MongoDB Java Sync Driver.
 *
 * <p>Implements {@link MongoDriverInterface} by dispatching {@link MongoResult}
 * operations to the appropriate MongoDB client methods.</p>
 *
 * <h3>Usage:</h3>
 * <pre>
 * MongoDriver driver = MongoDriver.connect("mongodb://localhost:27017", "mydb");
 * // or
 * MongoDriver driver = MongoDriver.mustConnect("mongodb://localhost:27017", "mydb");
 * </pre>
 */
public class MongoDriver implements MongoDriverInterface {

    private final MongoClient client;
    private final MongoDatabase database;

    /**
     * Create a MongoDriver from an existing MongoClient and database name.
     */
    public MongoDriver(MongoClient client, String dbName) {
        this.client = client;
        this.database = client.getDatabase(dbName);
    }

    /**
     * Create a MongoDriver from an existing MongoDatabase.
     */
    public MongoDriver(MongoClient client, MongoDatabase database) {
        this.client = client;
        this.database = database;
    }

    /**
     * Connect to MongoDB and return a MongoDriver. Returns null on failure.
     *
     * @param uri    MongoDB connection URI
     * @param dbName database name
     * @return a MongoDriver, or null if connection fails
     */
    public static MongoDriver connect(String uri, String dbName) {
        try {
            MongoClient client = MongoClients.create(uri);
            return new MongoDriver(client, dbName);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Connect to MongoDB and return a MongoDriver. Throws on failure.
     *
     * @param uri    MongoDB connection URI
     * @param dbName database name
     * @return a MongoDriver
     * @throws RuntimeException if connection fails
     */
    public static MongoDriver mustConnect(String uri, String dbName) {
        try {
            MongoClient client = MongoClients.create(uri);
            return new MongoDriver(client, dbName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to MongoDB at " + uri + ": " + e.getMessage(), e);
        }
    }

    /**
     * Return the underlying MongoDatabase instance.
     */
    public MongoDatabase getDatabase() {
        return database;
    }

    @Override
    public List<Map<String, Object>> executeFind(MongoResult result) {
        MongoCollection<Document> coll = database.getCollection(result.collection);

        Document filter = toDocument(result.filter);
        Document projection = result.projection != null ? toDocument(result.projection) : null;
        Document sort = result.sort != null ? toDocument(result.sort) : null;

        var cursor = coll.find(filter);
        if (projection != null) cursor = cursor.projection(projection);
        if (sort != null) cursor = cursor.sort(sort);
        if (result.skip > 0) cursor = cursor.skip(result.skip);
        if (result.limit > 0) cursor = cursor.limit(result.limit);

        List<Map<String, Object>> results = new ArrayList<>();
        for (Document doc : cursor) {
            results.add(cleanDocument(doc));
        }
        return results;
    }

    @Override
    public List<Map<String, Object>> executeAggregate(MongoResult result) {
        MongoCollection<Document> coll = database.getCollection(result.collection);

        List<Document> pipeline = new ArrayList<>();
        if (result.pipeline != null) {
            for (Map<String, Object> stage : result.pipeline) {
                pipeline.add(toDocument(stage));
            }
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Document doc : coll.aggregate(pipeline)) {
            results.add(cleanDocument(doc));
        }
        return results;
    }

    @Override
    public Map<String, Object> executeInsert(MongoResult result) {
        MongoCollection<Document> coll = database.getCollection(result.collection);
        Document doc = toDocument(result.document);
        coll.insertOne(doc);
        Map<String, Object> inserted = cleanDocument(doc);
        return inserted;
    }

    @Override
    public long executeUpdate(MongoResult result) {
        MongoCollection<Document> coll = database.getCollection(result.collection);
        Document filter = toDocument(result.filter);
        Document update = toDocument(result.update);
        UpdateResult updateResult = coll.updateMany(filter, update);
        return updateResult.getModifiedCount();
    }

    @Override
    public long executeDelete(MongoResult result) {
        MongoCollection<Document> coll = database.getCollection(result.collection);
        Document filter = toDocument(result.filter);
        DeleteResult deleteResult = coll.deleteMany(filter);
        return deleteResult.getDeletedCount();
    }

    @Override
    public void close() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    // ── Internal helpers ───────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Document toDocument(Map<String, Object> map) {
        if (map == null) return new Document();
        Document doc = new Document();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof Map) {
                val = toDocument((Map<String, Object>) val);
            } else if (val instanceof List) {
                val = convertList((List<?>) val);
            }
            doc.put(entry.getKey(), val);
        }
        return doc;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> convertList(List<?> list) {
        List<Object> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map) {
                result.add(toDocument((Map<String, Object>) item));
            } else if (item instanceof List) {
                result.add(convertList((List<?>) item));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Convert a MongoDB Document to a plain Map, removing _id.
     */
    private static Map<String, Object> cleanDocument(Document doc) {
        Map<String, Object> map = new LinkedHashMap<>(doc);
        map.remove("_id");
        return map;
    }
}
