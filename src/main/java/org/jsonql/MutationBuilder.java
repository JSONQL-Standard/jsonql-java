package org.jsonql;

import java.util.*;

/**
 * Fluent API for building JSONQL mutations (create, update, delete) programmatically.
 *
 * <p>Usage:
 *
 * <pre>
 * // Create
 * Map&lt;String, Object&gt; mutation = new MutationBuilder()
 *     .create("users", Map.of("name", "Alice", "email", "alice@example.com"))
 *     .build();
 *
 * // Update
 * Map&lt;String, Object&gt; mutation = new MutationBuilder()
 *     .update("users", Map.of("name", "Bob"))
 *     .where(Conditions.field("id", Conditions.eq(1)))
 *     .build();
 *
 * // Delete
 * Map&lt;String, Object&gt; mutation = new MutationBuilder()
 *     .delete("users")
 *     .where(Conditions.field("id", Conditions.eq(1)))
 *     .build();
 * </pre>
 */
public class MutationBuilder {

    private Map<String, Object> mutation;

    /** Initialize a create (INSERT) mutation. */
    public MutationBuilder create(String from, Map<String, Object> data) {
        mutation = new LinkedHashMap<>();
        mutation.put("op", "create");
        mutation.put("from", from);
        mutation.put("data", new LinkedHashMap<>(data));
        return this;
    }

    /** Initialize an update mutation. */
    public MutationBuilder update(String from, Map<String, Object> patch) {
        mutation = new LinkedHashMap<>();
        mutation.put("op", "update");
        mutation.put("from", from);
        mutation.put("patch", new LinkedHashMap<>(patch));
        return this;
    }

    /** Initialize a delete mutation. */
    public MutationBuilder delete(String from) {
        mutation = new LinkedHashMap<>();
        mutation.put("op", "delete");
        mutation.put("from", from);
        return this;
    }

    /** Set the WHERE clause for the mutation. */
    public MutationBuilder where(Map<String, Object> where) {
        ensureInitialized();
        mutation.put("where", where);
        return this;
    }

    /** Set the fields to return after the mutation. */
    public MutationBuilder fields(String... fields) {
        ensureInitialized();
        mutation.put("fields", Arrays.asList(fields));
        return this;
    }

    /** Set the limit for the mutation (e.g. update/delete limit). */
    public MutationBuilder limit(int limit) {
        ensureInitialized();
        mutation.put("limit", limit);
        return this;
    }

    /** Build and return the mutation as a Map. */
    public Map<String, Object> build() {
        ensureInitialized();
        return new LinkedHashMap<>(mutation);
    }

    /** Reset the builder to a clean state. */
    public MutationBuilder reset() {
        mutation = null;
        return this;
    }

    private void ensureInitialized() {
        if (mutation == null) {
            throw new IllegalStateException(
                    "Mutation not initialized: call create(), update(), or delete() first");
        }
    }
}
