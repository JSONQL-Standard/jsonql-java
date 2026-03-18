package org.jsonql;

import java.util.List;
import java.util.Map;

/**
 * Interface for MongoDB operations.
 *
 * <p>Unlike the SQL {@link JsonQLDriver}, this operates on
 * {@link MongoResult} descriptors rather than raw SQL strings.</p>
 *
 * <p>Implementations should dispatch based on
 * {@code result.operation} (find, aggregate, insertOne, updateMany,
 * deleteMany) to the appropriate MongoDB client method.</p>
 */
public interface MongoDriverInterface {

    /**
     * Execute a find query and return the matching documents.
     *
     * @param result transpiled MongoResult with operation "find"
     * @return list of documents as maps
     */
    List<Map<String, Object>> executeFind(MongoResult result) throws Exception;

    /**
     * Execute an aggregation pipeline and return the results.
     *
     * @param result transpiled MongoResult with operation "aggregate"
     * @return list of aggregation results as maps
     */
    List<Map<String, Object>> executeAggregate(MongoResult result) throws Exception;

    /**
     * Insert a single document.
     *
     * @param result transpiled MongoResult with operation "insertOne"
     * @return the inserted document (with generated _id if applicable)
     */
    Map<String, Object> executeInsert(MongoResult result) throws Exception;

    /**
     * Update documents matching the filter.
     *
     * @param result transpiled MongoResult with operation "updateMany"
     * @return number of modified documents
     */
    long executeUpdate(MongoResult result) throws Exception;

    /**
     * Delete documents matching the filter.
     *
     * @param result transpiled MongoResult with operation "deleteMany"
     * @return number of deleted documents
     */
    long executeDelete(MongoResult result) throws Exception;

    /**
     * Close the MongoDB connection.
     */
    void close() throws Exception;
}
