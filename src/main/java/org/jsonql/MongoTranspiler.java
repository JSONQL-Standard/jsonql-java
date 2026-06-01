package org.jsonql;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Converts JSONQL queries and mutations into MongoDB operation descriptors. Produces MongoResult
 * objects containing filter documents, projections, sort specs, and aggregation pipelines.
 */
public class MongoTranspiler {

    /** Known WHERE operators — anything else raises an error. */
    private static final Set<String> KNOWN_OPS =
            Set.of(
                    "eq",
                    "neq",
                    "ne",
                    "gt",
                    "gte",
                    "lt",
                    "lte",
                    "like",
                    "in",
                    "nin",
                    "contains",
                    "starts",
                    "ends");

    public MongoResult transpile(Map<String, Object> query, String collection) {
        MongoResult result = new MongoResult(collection, "find");

        // WHERE -> filter
        if (query.containsKey("where")) {
            Object where = query.get("where");
            if (where instanceof Map) {
                result.filter = processWhere((Map<?, ?>) where);
            }
        }

        // FIELDS -> projection
        if (query.containsKey("fields")) {
            Object fields = query.get("fields");
            if (fields instanceof List) {
                Map<String, Object> projection = new LinkedHashMap<>();
                for (Object f : (List<?>) fields) {
                    projection.put(f.toString(), 1);
                }
                result.projection = projection;
            }
        }

        // SORT
        if (query.containsKey("sort")) {
            Object sort = query.get("sort");
            if (sort != null
                    && !(sort instanceof String)
                    && !(sort instanceof List)
                    && !(sort instanceof Map)) {
                throw new IllegalArgumentException("sort must be a string, object, or array");
            }
            Map<String, Object> sortDoc = new LinkedHashMap<>();
            List<String> sortItems = new ArrayList<>();
            if (sort instanceof String) {
                sortItems.add((String) sort);
            } else if (sort instanceof List) {
                for (Object s : (List<?>) sort) {
                    sortItems.add(s.toString());
                }
            }
            for (String s : sortItems) {
                if (s.startsWith("-")) {
                    sortDoc.put(s.substring(1), -1);
                } else {
                    sortDoc.put(s, 1);
                }
            }
            result.sort = sortDoc;
        }

        // LIMIT
        if (query.containsKey("limit")) {
            Object l = query.get("limit");
            if (l instanceof Number) result.limit = ((Number) l).intValue();
        }

        // SKIP
        if (query.containsKey("skip")) {
            Object s = query.get("skip");
            if (s instanceof Number) result.skip = ((Number) s).intValue();
        }

        // DISTINCT → aggregation pipeline with $group
        Object distinctObj = query.get("distinct");
        boolean hasDistinct = false;
        if (distinctObj instanceof Boolean) {
            hasDistinct = (Boolean) distinctObj;
        } else if (distinctObj instanceof Map) {
            hasDistinct = true;
        } else if (distinctObj instanceof List) {
            hasDistinct = true;
        }

        if (hasDistinct && !query.containsKey("aggregate")) {
            result.operation = "aggregate";
            List<Map<String, Object>> pipeline = new ArrayList<>();

            // $match stage
            if (!result.filter.isEmpty()) {
                pipeline.add(Map.of("$match", result.filter));
            }

            // Determine fields for distinct
            List<String> distinctFields = new ArrayList<>();
            if (distinctObj instanceof List) {
                // distinct: ["field1", "field2"] — array of field names
                for (Object f : (List<?>) distinctObj) {
                    distinctFields.add(f.toString());
                }
            } else if (distinctObj instanceof Map) {
                Map<?, ?> dMap = (Map<?, ?>) distinctObj;
                Object fieldsObj = dMap.get("fields");
                if (fieldsObj instanceof List) {
                    for (Object f : (List<?>) fieldsObj) {
                        distinctFields.add(f.toString());
                    }
                }
            }
            // Fallback to query.fields
            if (distinctFields.isEmpty() && query.containsKey("fields")) {
                Object fields = query.get("fields");
                if (fields instanceof List) {
                    for (Object f : (List<?>) fields) {
                        distinctFields.add(f.toString());
                    }
                }
            }

            if (!distinctFields.isEmpty()) {
                // $group stage — _id is the combination of distinct fields
                Map<String, Object> groupId = new LinkedHashMap<>();
                Map<String, Object> groupStage = new LinkedHashMap<>();
                for (String f : distinctFields) {
                    groupId.put(f, "$" + f);
                    groupStage.put(f, Map.of("$first", "$" + f));
                }
                groupStage.put("_id", groupId);
                pipeline.add(Map.of("$group", groupStage));

                // $project stage — keep only requested fields, hide _id
                Map<String, Object> projectStage = new LinkedHashMap<>();
                projectStage.put("_id", 0);
                for (String f : distinctFields) {
                    projectStage.put(f, 1);
                }
                pipeline.add(Map.of("$project", projectStage));
            }

            // Sort stage
            if (result.sort != null) {
                pipeline.add(Map.of("$sort", result.sort));
            }
            // Skip/Limit stages
            if (result.skip > 0) {
                pipeline.add(Map.of("$skip", result.skip));
            }
            if (result.limit > 0) {
                pipeline.add(Map.of("$limit", result.limit));
            }

            result.pipeline = pipeline;
            return result;
        }

        // AGGREGATE -> aggregation pipeline
        if (query.containsKey("aggregate")) {
            result.operation = "aggregate";
            List<Map<String, Object>> pipeline = new ArrayList<>();

            // $match stage
            if (!result.filter.isEmpty()) {
                Map<String, Object> matchStage = new LinkedHashMap<>();
                matchStage.put("$match", result.filter);
                pipeline.add(matchStage);
            }

            // $group stage
            Map<String, Object> groupStage = new LinkedHashMap<>();
            if (query.containsKey("groupBy") && query.get("groupBy") instanceof List) {
                List<?> groupBy = (List<?>) query.get("groupBy");
                Map<String, Object> groupId = new LinkedHashMap<>();
                for (Object g : groupBy) {
                    String gStr = g.toString();
                    groupId.put(gStr, "$" + gStr);
                    groupStage.put(gStr, Map.of("$first", "$" + gStr));
                }
                groupStage.put("_id", groupId);
            } else {
                groupStage.put("_id", null);
            }

            Object aggObj = query.get("aggregate");
            if (aggObj instanceof Map) {
                Map<?, ?> aggs = (Map<?, ?>) aggObj;
                for (Map.Entry<?, ?> entry : aggs.entrySet()) {
                    String alias = entry.getKey().toString();
                    Object funcObj = entry.getValue();
                    if (funcObj instanceof Map) {
                        Map<?, ?> funcMap = (Map<?, ?>) funcObj;
                        for (Map.Entry<?, ?> funcEntry : funcMap.entrySet()) {
                            String func = funcEntry.getKey().toString().toLowerCase();
                            String field = funcEntry.getValue().toString();

                            switch (func) {
                                case "count":
                                    if ("*".equals(field)) {
                                        groupStage.put(alias, Map.of("$sum", 1));
                                    } else {
                                        groupStage.put(
                                                alias,
                                                Map.of(
                                                        "$sum",
                                                        Map.of(
                                                                "$cond",
                                                                java.util.Arrays.asList(
                                                                        Map.of(
                                                                                "$ne",
                                                                                java.util.Arrays
                                                                                        .asList(
                                                                                                "$"
                                                                                                        + field,
                                                                                                null)),
                                                                        1,
                                                                        0))));
                                    }
                                    break;
                                case "sum":
                                    groupStage.put(alias, Map.of("$sum", "$" + field));
                                    break;
                                case "avg":
                                    groupStage.put(alias, Map.of("$avg", "$" + field));
                                    break;
                                case "min":
                                    groupStage.put(alias, Map.of("$min", "$" + field));
                                    break;
                                case "max":
                                    groupStage.put(alias, Map.of("$max", "$" + field));
                                    break;
                            }
                        }
                    }
                }
            }

            pipeline.add(Map.of("$group", groupStage));

            if (result.sort != null) {
                pipeline.add(Map.of("$sort", result.sort));
            }
            if (result.skip > 0) {
                pipeline.add(Map.of("$skip", result.skip));
            }
            if (result.limit > 0) {
                pipeline.add(Map.of("$limit", result.limit));
            }

            result.pipeline = pipeline;
        }

        return result;
    }

    public MongoResult transpileInsert(Map<String, Object> data, String collection) {
        MongoResult result = new MongoResult(collection, "insertOne");
        result.document = data;
        return result;
    }

    public MongoResult transpileInsertMany(List<Map<String, Object>> data, String collection) {
        MongoResult result = new MongoResult(collection, "insertMany");
        result.documents = data;
        return result;
    }

    public MongoResult transpileUpdate(
            Map<String, Object> data, Map<String, Object> where, String collection) {
        MongoResult result = new MongoResult(collection, "updateMany");
        if (where != null) {
            result.filter = processWhere(where);
        }
        result.update = Map.of("$set", data);
        return result;
    }

    public MongoResult transpileDelete(Map<String, Object> where, String collection) {
        MongoResult result = new MongoResult(collection, "deleteMany");
        if (where != null) {
            result.filter = processWhere(where);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> processWhere(Map<?, ?> where) {
        Map<String, Object> filter = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : where.entrySet()) {
            String field = entry.getKey().toString();
            Object cond = entry.getValue();

            // Handle "or" logical operator
            if ("or".equals(field) || "OR".equals(field)) {
                if (cond instanceof List) {
                    List<Map<String, Object>> orConditions = new ArrayList<>();
                    for (Object item : (List<?>) cond) {
                        if (item instanceof Map) {
                            orConditions.add(processWhere((Map<?, ?>) item));
                        }
                    }
                    if (!orConditions.isEmpty()) {
                        filter.put("$or", orConditions);
                    }
                }
                continue;
            }

            // Handle "and" logical operator
            if ("and".equals(field) || "AND".equals(field)) {
                if (cond instanceof List) {
                    List<Map<String, Object>> andConditions = new ArrayList<>();
                    for (Object item : (List<?>) cond) {
                        if (item instanceof Map) {
                            andConditions.add(processWhere((Map<?, ?>) item));
                        }
                    }
                    if (!andConditions.isEmpty()) {
                        filter.put("$and", andConditions);
                    }
                }
                continue;
            }

            // Handle "not" logical operator
            if ("not".equals(field) || "NOT".equals(field)) {
                if (cond instanceof Map) {
                    Map<String, Object> subFilter = processWhere((Map<?, ?>) cond);
                    if (!subFilter.isEmpty()) {
                        filter.put("$nor", List.of(subFilter));
                    }
                }
                continue;
            }

            if (cond instanceof Map) {
                Map<?, ?> condMap = (Map<?, ?>) cond;
                Map<String, Object> mongoOp = new LinkedHashMap<>();
                boolean handled = false;

                if (condMap.containsKey("eq")) {
                    filter.put(field, condMap.get("eq"));
                    continue;
                }
                if (condMap.containsKey("neq")) {
                    handled = true;
                    mongoOp.put("$ne", condMap.get("neq"));
                }
                if (condMap.containsKey("ne")) {
                    handled = true;
                    mongoOp.put("$ne", condMap.get("ne"));
                }
                if (condMap.containsKey("gt")) {
                    handled = true;
                    mongoOp.put("$gt", condMap.get("gt"));
                }
                if (condMap.containsKey("gte")) {
                    handled = true;
                    mongoOp.put("$gte", condMap.get("gte"));
                }
                if (condMap.containsKey("lt")) {
                    handled = true;
                    mongoOp.put("$lt", condMap.get("lt"));
                }
                if (condMap.containsKey("lte")) {
                    handled = true;
                    mongoOp.put("$lte", condMap.get("lte"));
                }
                if (condMap.containsKey("like")) {
                    handled = true;
                    String pattern =
                            condMap.get("like").toString().replace("%", ".*").replace("_", ".");
                    mongoOp.put("$regex", pattern);
                    mongoOp.put("$options", "i");
                }
                if (condMap.containsKey("in")) {
                    handled = true;
                    Object val = condMap.get("in");
                    if (val instanceof List) {
                        mongoOp.put("$in", val);
                    }
                }
                if (condMap.containsKey("nin")) {
                    handled = true;
                    Object val = condMap.get("nin");
                    if (val instanceof List) {
                        mongoOp.put("$nin", val);
                    }
                }
                if (condMap.containsKey("contains")) {
                    handled = true;
                    String s = condMap.get("contains").toString();
                    mongoOp.put("$regex", escapeRegex(s));
                    mongoOp.put("$options", "i");
                }
                if (condMap.containsKey("starts")) {
                    handled = true;
                    String s = condMap.get("starts").toString();
                    mongoOp.put("$regex", "^" + escapeRegex(s));
                    mongoOp.put("$options", "i");
                }
                if (condMap.containsKey("ends")) {
                    handled = true;
                    String s = condMap.get("ends").toString();
                    mongoOp.put("$regex", escapeRegex(s) + "$");
                    mongoOp.put("$options", "i");
                }

                // Unknown operator validation
                if (!handled && !condMap.isEmpty()) {
                    for (Object op : condMap.keySet()) {
                        String opStr = op.toString();
                        if (!KNOWN_OPS.contains(opStr)) {
                            throw new JsonQLTranspileException(
                                    "Unknown operator \""
                                            + opStr
                                            + "\" for field \""
                                            + field
                                            + "\"");
                        }
                    }
                }

                if (!mongoOp.isEmpty()) {
                    filter.put(field, mongoOp);
                }
            } else {
                filter.put(field, cond);
            }
        }

        return filter;
    }

    /**
     * Escape regex metacharacters so user input is matched literally in a MongoDB {@code $regex}
     * (PCRE). Unlike {@link Pattern#quote}, this does not rely on {@code \Q...\E}, which MongoDB's
     * PCRE engine does not honor.
     */
    private static String escapeRegex(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (".^$*+?()[]{}|\\".indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
