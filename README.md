# jsonql-java

The official Java SDK for **JSONQL**.

[![CI](https://github.com/JSONQL-Standard/jsonql-java/actions/workflows/ci.yml/badge.svg)](https://github.com/JSONQL-Standard/jsonql-java/actions/workflows/ci.yml)
[![Java Version](https://img.shields.io/badge/java-%3E%3D17-blue)](https://adoptium.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

| | |
|---|---|
| **Group** | `org.jsonql` |
| **Artifact** | `jsonql-java` |
| **Java** | ≥ 17 |

**JSONQL** is a secure, lightweight, and polyglot JSON-based query language for filtering, sorting, pagination, field selection, and mutations in RESTful APIs.

## Features

- **JSONQL v1.1 Parser** — parse and validate incoming JSON queries and mutations
- **Query Builder** — fluent API with condition helpers (`eq`, `gt`, `and`, `or`, etc.)
- **Mutation Builder** — fluent API for create / update / delete
- **SQL Transpiler** — convert parsed queries → parameterized SQL (PostgreSQL, MySQL, SQLite, MSSQL)
- **MongoDB Transpiler** — convert parsed queries → MongoDB aggregation pipelines
- **Schema Validation** — permission checking and field-level validation
- **Result Hydrator** — flatten SQL JOIN rows into nested JSON trees
- **Driver Factory** — `createDriver()` with auto-config from environment variables
- **Lifecycle Hooks** — 20 hooks: `beforeParse`, `beforeTranspile`, `beforeExecute`, `afterCreate`, etc.
- **Condition Helpers** — `eq`, `gt`, `contains`, `and`, `or`, `not`, etc.
- **Spring Boot Adapter** — full request handling with parse → validate → transpile → execute
- **Jakarta EE / JAX-RS Adapter** — same pipeline for Jakarta EE applications
- **MongoDB Adapter** — Spring Boot and Jakarta EE adapters for MongoDB

## Installation

### Maven

```xml
<dependency>
    <groupId>org.jsonql</groupId>
    <artifactId>jsonql-java</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'org.jsonql:jsonql-java:1.0.0'
```

## Quick Start

A working JSONQL API in under 20 lines:

```java
import org.jsonql.*;
import org.jsonql.adapter.spring.SpringAdapter;
import org.jsonql.adapter.AdapterOptions;

@RestController
public class JsonqlController {

    private final SpringAdapter adapter;

    public JsonqlController(DataSource ds) {
        var schema = JsonQLFactory.mustLoadSchema("schema.json");
        adapter = new SpringAdapter(new AdapterOptions("postgres", schema, ds::getConnection));
    }

    @RequestMapping("/{table}")
    public ResponseEntity<Object> handle(HttpMethod method, @PathVariable String table,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(required = false) Map<String, String> params) {
        return adapter.handle(method.name(), table, body, params);
    }
}
```

<details>
<summary>schema.json</summary>

```json
{
  "tables": {
    "users": {
      "fields": {
        "id":   { "type": "number" },
        "name": { "type": "string" },
        "age":  { "type": "number" }
      }
    }
  }
}
```
</details>

> **Prefer inline?** Replace `mustLoadSchema(...)` with `new JsonQLSchema(Map.of(...))` — see [Schema Validation](#schema-validation).

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/mydb
export SPRING_DATASOURCE_USERNAME=user
export SPRING_DATASOURCE_PASSWORD=pass
mvn spring-boot:run
```

```bash
curl -s -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"fields":["id","name"],"where":{"age":{"gt":18}},"sort":["name"],"limit":10}'
```

```json
{
  "data": [
    { "id": 1, "name": "Alice" },
    { "id": 2, "name": "Bob" }
  ]
}
```

## Builders

### Query Builder

```java
import org.jsonql.QueryBuilder;
import static org.jsonql.Conditions.*;

Map<String, Object> query = new QueryBuilder()
    .fields("id", "name", "email")
    .where(and(
        field("status", eq("active")),
        field("age", gt(18))
    ))
    .sort("name", "-created_at")
    .limit(10)
    .build();
```

### Mutation Builder

```java
import org.jsonql.MutationBuilder;
import static org.jsonql.Conditions.*;

// Create
Map<String, Object> create = new MutationBuilder()
    .create("users", Map.of("name", "Alice", "email", "alice@example.com"))
    .build();

// Update
Map<String, Object> update = new MutationBuilder()
    .update("users", Map.of("name", "Alice Smith"))
    .where(field("id", eq(1)))
    .build();

// Delete
Map<String, Object> delete = new MutationBuilder()
    .delete("users")
    .where(field("id", eq(1)))
    .build();
```

## Transpilers

### SQL Transpiler

```java
import org.jsonql.SQLTranspiler;
import org.jsonql.dialect.PostgresDialect;

var transpiler = new SQLTranspiler(new PostgresDialect());
var result = transpiler.transpile(query, "users", schema);
System.out.println(result.getSql());
// SELECT "users"."id", "users"."name" FROM "users" WHERE "users"."status" = ? AND "users"."age" > ? ...
System.out.println(result.getParams()); // [active, 18]
```

### MongoDB Transpiler

```java
import org.jsonql.MongoTranspiler;

var transpiler = new MongoTranspiler();
MongoResult result = transpiler.transpile(query, "users");
// result.getCollection() = "users"
// result.getOperation()  = "find"
// result.getFilter()     = {"status": "active", "age": {"$gt": 18}}
```

## Schema Validation

```java
import org.jsonql.JsonQLFactory;
import org.jsonql.schema.*;

// Load from file
JsonQLSchema schema = JsonQLFactory.mustLoadSchema("schema.json");

// Or build inline
var usersTable = new JsonQLTableSchema();
usersTable.getFields().put("id", new JsonQLFieldSchema());
usersTable.getFields().put("name", new JsonQLFieldSchema());
usersTable.getFields().put("password", new JsonQLFieldSchema() {{
    setAllowFilter(false);  // block filtering on password
    setAllowSelect(false);  // block selecting password
}});

JsonQLSchema schema = new JsonQLSchema(Map.of("users", usersTable));
```

## Result Hydrator

```java
import org.jsonql.hydrator.ResultHydrator;

var hydrator = new ResultHydrator();

// Flatten SQL JOIN rows into nested JSON
// Rows with "posts__id", "posts__title" columns → nested "posts" array
List<Map<String, Object>> results = hydrator.hydrate(resultSet);
// [{"id": 1, "name": "Alice", "posts": [{"id": 10, "title": "Hello"}, ...]}]
```

## Framework Adapters

### Spring Boot

```java
import org.jsonql.adapter.spring.SpringAdapter;
import org.jsonql.adapter.AdapterOptions;

var schema = JsonQLFactory.mustLoadSchema("schema.json");
var options = new AdapterOptions("postgres", schema, dataSource::getConnection);
var adapter = new SpringAdapter(options);

// In your controller:
@RequestMapping("/{table}")
public ResponseEntity<Object> handle(HttpMethod method, @PathVariable String table,
        @RequestBody(required = false) Map<String, Object> body,
        @RequestParam(required = false) Map<String, String> params) {
    return adapter.handle(method.name(), table, body, params);
}
```

### Jakarta EE / JAX-RS

```java
import org.jsonql.adapter.jakarta.JakartaAdapter;
import org.jsonql.adapter.AdapterOptions;

var schema = JsonQLFactory.mustLoadSchema("schema.json");
var options = new AdapterOptions("postgres", schema, dataSource::getConnection);
var adapter = new JakartaAdapter(options);

@Path("/{table}")
public Response handle(@PathParam("table") String table,
        Map<String, Object> body, @Context UriInfo uriInfo) {
    return adapter.handle("POST", table, body, flattenQueryParams(uriInfo));
}
```

### MongoDB (Spring Boot)

```java
import org.jsonql.adapter.spring.SpringMongoAdapter;
import org.jsonql.adapter.MongoAdapterOptions;

var db = MongoDriver.mustConnect("mongodb://localhost:27017", "mydb");
var adapter = new SpringMongoAdapter(new MongoAdapterOptions(schema, db));
```

## HTTP Request Normalization

`JsonQLRequestNormalizer.normalize()` converts HTTP semantics into a unified JSONQL query map:

- **GET** → always treated as SELECT
- **POST** → auto-detected as SELECT (body has `fields`, `where`, etc.) or INSERT
- **PATCH / PUT** → treated as UPDATE (non-keyword body keys extracted to `patch`)
- **DELETE** → treated as DELETE (non-keyword body keys extracted to `where`)
- Query params `?q={...}` or `?query={...}` are parsed and merged with the body
- Non-keyword params become implicit WHERE conditions

## Lifecycle Hooks

```java
engine.execute(conn, "users", query, new JsonQLLifecycle() {
    @Override
    public Map<String, Object> beforeParse(Map<String, Object> query) {
        // Add tenant isolation, modify query
        query.put("where", Conditions.and(
            (Map<String, Object>) query.get("where"),
            Conditions.field("tenant_id", Conditions.eq(currentTenantId))
        ));
        return query;
    }

    @Override
    public void beforeExecute(String sql, List<Object> params) {
        log.info("SQL: {} | Params: {}", sql, params);
    }

    @Override
    public void afterExecute(List<Map<String, Object>> results) {
        log.info("Returned {} rows", results.size());
    }

    @Override
    public Map<String, Object> beforeCreate(Map<String, Object> mutation) {
        mutation.put("created_at", Instant.now().toString());
        return mutation;
    }
});
```

**Full hook pipeline:**

| Stage | Query | Create | Update | Delete |
|-------|:-----:|:------:|:------:|:------:|
| `beforeParse` | ✅ | ✅ | ✅ | ✅ |
| `afterParse` | ✅ | ✅ | ✅ | ✅ |
| `beforeValidate` | ✅ | ✅ | ✅ | ✅ |
| `afterValidate` | ✅ | ✅ | ✅ | ✅ |
| `beforeCreate` / `beforeUpdate` / `beforeDelete` | — | ✅ | ✅ | ✅ |
| `beforeTranspile` | ✅ | ✅ | ✅ | ✅ |
| `afterTranspile` | ✅ | ✅ | ✅ | ✅ |
| `beforeExecute` | ✅ | ✅ | ✅ | ✅ |
| `afterExecute` | ✅ | ✅ | ✅ | ✅ |
| `afterCreate` / `afterUpdate` / `afterDelete` | — | ✅ | ✅ | ✅ |
| `beforeHydrate` | ✅ | — | — | — |
| `afterHydrate` | ✅ | — | — | — |

## Core API

| Class | Purpose |
|-------|---------|
| `JsonQLEngine` | Transpile and execute JSONQL queries via builder pattern |
| `JsonQLFactory` | Static helpers: `loadSchema`, `mustLoadSchema`, `envOr`, `createDriver` |
| `SQLTranspiler` | Generate parameterized SQL from JSONQL query maps |
| `MongoTranspiler` | Generate MongoDB operations from JSONQL query maps |
| `JsonQLRequestNormalizer` | Convert HTTP method + body + params → JSONQL query |
| `JsonQLResult` | Wrap execution results: `getData()`, `toResponseBody()`, `isMutation()` |
| `JsonQLLifecycle` | 20 lifecycle hooks interface with default no-op implementations |
| `QueryBuilder` | Fluent query construction |
| `MutationBuilder` | Fluent mutation construction |
| `Conditions` | Static condition helpers (`eq`, `gt`, `and`, `or`, etc.) |
| `ResultHydrator` | Flatten SQL JOIN rows → nested JSON trees |
| `SpringAdapter` | Spring Boot handler with full pipeline |
| `JakartaAdapter` | Jakarta EE / JAX-RS handler with full pipeline |

## Supported Dialects

| Dialect    | Placeholder | Quoting      | RETURNING |
|------------|-------------|--------------|-----------|
| `postgres` | `?`         | `"col"`      | ✅        |
| `mysql`    | `?`         | `` `col` ``  | ❌        |
| `sqlite`   | `?`         | `"col"`      | ❌        |
| `mssql`    | `?`         | `[col]`      | ❌        |

## Condition Helpers

```java
import static org.jsonql.Conditions.*;

eq(value)           // {"eq": value}
neq(value)          // {"neq": value}
gt(value)           // {"gt": value}
gte(value)          // {"gte": value}
lt(value)           // {"lt": value}
lte(value)          // {"lte": value}
in(values...)       // {"in": [...]}
nin(values...)      // {"nin": [...]}
like(pattern)       // {"like": pattern}
contains(value)     // {"like": "%value%"}
startsWith(value)   // {"like": "value%"}
endsWith(value)     // {"like": "%value"}
field(name, cond)   // {name: cond}
field(name, value)  // {name: value}
and(conditions...)  // {"and": [...]}
or(conditions...)   // {"or": [...]}
not(condition)      // {"not": condition}
```

## Error Hierarchy

```
JsonQLException
├── JsonQLValidationException   (carries List<String> errors)
├── JsonQLTranspileException
├── JsonQLExecutionException
└── JsonQLHookException
```

## Compliance

All 4 Java integration adapters pass the full compliance test suite:

| Adapter | Type | PostgreSQL |
|---------|------|:----------:|
| **Spring Boot** | simple | ✅ |
| **Spring Boot** | lifecycle | ✅ |
| **Jakarta EE** | simple | ✅ |
| **Jakarta EE** | lifecycle | ✅ |

Tests run via [jsonql-tests](https://github.com/JSONQL-Standard/jsonql-tests).

## Development

```bash
mvn compile          # compile
mvn test             # run tests
mvn package          # build JAR
mvn install          # install to local Maven repo
```

## Links

- 📋 [JSONQL Spec](https://github.com/JSONQL-Standard/jsonql-spec)
- 🧪 [Compliance Tests](https://github.com/JSONQL-Standard/jsonql-tests)
- 🐛 [Issues](https://github.com/JSONQL-Standard/jsonql-java/issues)

## License

MIT
