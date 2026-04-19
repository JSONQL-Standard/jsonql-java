package org.jsonql.adapter.spring;

import java.util.HashMap;
import java.util.Map;
import org.jsonql.JsonQLException;
import org.jsonql.JsonQLValidationException;
import org.jsonql.adapter.MongoAdapter;
import org.jsonql.adapter.MongoAdapterOptions;

/**
 * Spring Boot / Spring MVC adapter for JSONQL with MongoDB.
 *
 * <p>Wraps {@link MongoAdapter} to provide the same error-handling and {@link ResponseResult}
 * pattern as {@link SpringAdapter}.
 *
 * <h3>Usage in a Spring Boot {@code @RestController}:</h3>
 *
 * <pre>
 * &#64;RestController
 * &#64;RequestMapping("/api")
 * public class JsonQLMongoController {
 *
 *     private final SpringMongoAdapter adapter;
 *
 *     public JsonQLMongoController(MongoDriverInterface driver) {
 *         MongoAdapterOptions opts = new MongoAdapterOptions()
 *                 .driver(driver);
 *         this.adapter = new SpringMongoAdapter(opts);
 *     }
 *
 *     &#64;PostMapping("/{collection}")
 *     public ResponseEntity&lt;?&gt; handlePost(
 *             &#64;PathVariable String collection,
 *             &#64;RequestBody Map&lt;String, Object&gt; body) {
 *         var result = adapter.handle(body, "POST", collection);
 *         return ResponseEntity.status(result.status).body(result.body);
 *     }
 *
 *     &#64;GetMapping("/{collection}")
 *     public ResponseEntity&lt;?&gt; handleGet(
 *             &#64;PathVariable String collection,
 *             &#64;RequestParam Map&lt;String, String&gt; params) {
 *         Map&lt;String, Object&gt; body = new HashMap&lt;&gt;(params);
 *         var result = adapter.handle(body, "GET", collection);
 *         return ResponseEntity.status(result.status).body(result.body);
 *     }
 * }
 * </pre>
 */
public class SpringMongoAdapter {

    private final MongoAdapter handler;

    public SpringMongoAdapter(MongoAdapterOptions options) {
        this.handler = new MongoAdapter(options);
    }

    /**
     * Handle a JSONQL MongoDB request and return a framework-neutral response.
     *
     * @param body Parsed request body
     * @param httpMethod HTTP method string
     * @param pathName URL path segment (collection name)
     * @return ResponseResult with status code and body
     */
    public SpringAdapter.ResponseResult handle(
            Map<String, Object> body, String httpMethod, String pathName) {
        try {
            Map<String, Object> data = handler.processRequest(body, httpMethod, pathName);
            return new SpringAdapter.ResponseResult(200, data);
        } catch (JsonQLValidationException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Validation Error");
            error.put("details", e.getErrors());
            return new SpringAdapter.ResponseResult(400, error);
        } catch (JsonQLException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return new SpringAdapter.ResponseResult(400, error);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return new SpringAdapter.ResponseResult(403, error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Internal Server Error");
            error.put("details", e.getMessage());
            return new SpringAdapter.ResponseResult(500, error);
        }
    }
}
