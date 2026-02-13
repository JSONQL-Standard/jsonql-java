package org.jsonql;

import java.util.*;

/**
 * Converts JSONQL queries and mutations into MongoDB operation descriptors.
 * Produces MongoResult objects containing filter documents, projections,
 * sort specs, and aggregation pipelines.
 */
public class MongoTranspiler {

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
                                        groupStage.put(alias, Map.of("$sum",
                                            Map.of("$cond", java.util.Arrays.asList(
                                                Map.of("$ne", java.util.Arrays.asList("$" + field, null)),
                                                1, 0
                                            ))
                                        ));
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

    public MongoResult transpileUpdate(Map<String, Object> data, Map<String, Object> where, String collection) {
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

            if (cond instanceof Map) {
                Map<?, ?> condMap = (Map<?, ?>) cond;
                Map<String, Object> mongoOp = new LinkedHashMap<>();

                if (condMap.containsKey("eq")) {
                    filter.put(field, condMap.get("eq"));
                    continue;
                }
                if (condMap.containsKey("neq")) mongoOp.put("$ne", condMap.get("neq"));
                if (condMap.containsKey("gt")) mongoOp.put("$gt", condMap.get("gt"));
                if (condMap.containsKey("gte")) mongoOp.put("$gte", condMap.get("gte"));
                if (condMap.containsKey("lt")) mongoOp.put("$lt", condMap.get("lt"));
                if (condMap.containsKey("lte")) mongoOp.put("$lte", condMap.get("lte"));
                if (condMap.containsKey("like")) {
                    String pattern = condMap.get("like").toString()
                            .replace("%", ".*")
                            .replace("_", ".");
                    mongoOp.put("$regex", pattern);
                    mongoOp.put("$options", "i");
                }
                if (condMap.containsKey("in")) {
                    Object val = condMap.get("in");
                    if (val instanceof List) {
                        mongoOp.put("$in", val);
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
}
