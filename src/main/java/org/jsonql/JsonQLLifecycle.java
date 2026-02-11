package org.jsonql;

import java.util.List;
import java.util.Map;

/**
 * Lifecycle hooks for JSONQL query and mutation processing.
 * <p>
 * Implement any subset of these hooks to customize behavior at each stage
 * of the JSONQL pipeline. All methods have default no-op implementations.
 * <p>
 * Query pipeline: beforeParse → afterParse → beforeValidate → afterValidate → 
 *                 beforeTranspile → afterTranspile → beforeExecute → afterExecute →
 *                 beforeHydrate → afterHydrate
 * <p>
 * Mutation pipeline: beforeCreate/beforeUpdate/beforeDelete → beforeTranspile →
 *                    beforeExecute → afterExecute → afterCreate/afterUpdate/afterDelete
 */
public interface JsonQLLifecycle {

    // ---- Query Lifecycle Hooks ----

    /** Called before the query is parsed. Can modify the raw query map. */
    default Map<String, Object> beforeParse(Map<String, Object> query) { return query; }

    /** Called after parsing completes. */
    default void afterParse(Map<String, Object> query) {}

    /** Called before schema validation. */
    default Map<String, Object> beforeValidate(Map<String, Object> query) { return query; }

    /** Called after schema validation passes. */
    default void afterValidate(Map<String, Object> query) {}

    /** Called before the query is transpiled to SQL. Can modify the query. */
    default void beforeTranspile(Map<String, Object> query, String commandType) {}

    /** Called after transpilation with the generated SQL and parameters. */
    default void afterTranspile(String sql, List<Object> parameters) {}

    /** Called before the SQL is executed. Can modify SQL or parameters. */
    default void beforeExecute(String sql, List<Object> parameters) {}

    /** Called after SQL execution with the raw results. */
    default void afterExecute(List<Map<String, Object>> results) {}

    /** Called before result hydration (nesting). */
    default List<Map<String, Object>> beforeHydrate(List<Map<String, Object>> results) { return results; }

    /** Called after result hydration with the final nested results. */
    default List<Map<String, Object>> afterHydrate(List<Map<String, Object>> results) { return results; }

    // ---- Mutation Lifecycle Hooks ----

    /** Called before a create mutation is processed. Can modify the mutation data. */
    default Map<String, Object> beforeCreate(Map<String, Object> mutation) { return mutation; }

    /** Called after a create mutation completes. */
    default void afterCreate(Map<String, Object> mutation, Object result) {}

    /** Called before an update mutation is processed. Can modify the mutation data. */
    default Map<String, Object> beforeUpdate(Map<String, Object> mutation) { return mutation; }

    /** Called after an update mutation completes. */
    default void afterUpdate(Map<String, Object> mutation, Object result) {}

    /** Called before a delete mutation is processed. Can modify the mutation data. */
    default Map<String, Object> beforeDelete(Map<String, Object> mutation) { return mutation; }

    /** Called after a delete mutation completes. */
    default void afterDelete(Map<String, Object> mutation, Object result) {}
}
