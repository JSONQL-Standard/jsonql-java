# jsonql-java

A Java SDK for [JSONQL](../jsonql-spec/README.md) — parse, validate, and execute JSONQL queries against SQL databases.

## Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>org.jsonql</groupId>
    <artifactId>jsonql-java</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

```java
// 1. Create an engine
JsonQLEngine engine = JsonQLEngine.builder()
    .postgres()             // or .mysql(), .sqlite(), .dialect("postgres")
    .schema(schema)         // optional: enables validation & relationships
    .build();

// 2. Normalize an HTTP request into a JSONQL query
var request = JsonQLRequestNormalizer.normalize(
    "POST", "users",
    Map.of("fields", List.of("id", "name"), "where", Map.of("status", "active")),
    queryParams
);

// 3. Execute
try (Connection conn = dataSource.getConnection()) {
    JsonQLResult result = engine.executeRequest(conn, request);
    result.getData();          // List<Map<String, Object>>
    result.toResponseBody();   // {"data": [...]}
    result.isMutation();       // false (this was a SELECT)
}
```

## Core Classes

| Class | Purpose |
|-------|---------|
| `JsonQLEngine` | Transpiles and executes JSONQL queries |
| `JsonQLRequestNormalizer` | Converts HTTP requests to JSONQL query maps |
| `JsonQLResult` | Wraps execution results with response helpers |
| `SQLTranspiler` | Generates SQL from JSONQL (Postgres, MySQL, SQLite) |
| `JSONQLValidator` | Schema-based field and relation permission checking |
| `JsonQLLifecycle` | Hook interface: `beforeTranspile`, `beforeExecute`, `afterExecute` |

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
    public void beforeTranspile(Map<String, Object> query, String commandType) {
        // Modify query, add RLS filters, validate permissions
    }

    @Override
    public void beforeExecute(String sql, List<Object> params) {
        // Log SQL, audit queries
    }

    @Override
    public void afterExecute(List<Map<String, Object>> results) {
        // Transform results, trigger side effects
    }
});
```

## Framework Integration

### Spring Boot

```java
@Configuration
public class JsonqlConfig {
    @Bean
    public JsonQLEngine engine(JSONQLSchema schema) {
        return JsonQLEngine.builder().postgres().schema(schema).build();
    }
}

@RestController
public class QueryController {
    @Autowired JsonQLEngine engine;
    @Autowired JdbcTemplate jdbc;

    @RequestMapping("/{table}")
    public ResponseEntity<Object> handle(HttpMethod method,
            @PathVariable String table,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(required = false) Map<String, String> params) {
        var req = JsonQLRequestNormalizer.normalize(method.name(), table, body, params);
        try (var conn = jdbc.getDataSource().getConnection()) {
            var result = engine.executeRequest(conn, req);
            return ResponseEntity.ok(result.toResponseBody());
        }
    }
}
```

### Jakarta EE / JAX-RS

```java
@Path("/{table}")
public Response handle(@PathParam("table") String table,
        Map<String, Object> body, @Context UriInfo uriInfo) {
    var params = flattenQueryParams(uriInfo);
    var req = JsonQLRequestNormalizer.normalize("POST", table, body, params);
    try (var conn = dataSource.getConnection()) {
        var result = engine.executeRequest(conn, req);
        return Response.ok(result.toResponseBody()).build();
    }
}
```
