package org.jsonql.adapter.spring;

import org.jsonql.JsonQLException;
import org.jsonql.JsonQLValidationException;
import org.jsonql.adapter.AdapterOptions;
import org.jsonql.adapter.BaseHandler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Boot / Spring MVC adapter for JSONQL.
 *
 * <p>Usage in a Spring Boot {@code @RestController}:</p>
 * <pre>
 * &#64;RestController
 * &#64;RequestMapping("/api")
 * public class JsonQLController {
 *
 *     private final SpringAdapter adapter;
 *
 *     public JsonQLController(DataSource dataSource, JsonQLSchema schema) {
 *         AdapterOptions opts = new AdapterOptions()
 *             .dialect("postgres")
 *             .schema(schema)
 *             .connectionSupplier(() -> dataSource.getConnection());
 *         this.adapter = new SpringAdapter(opts);
 *     }
 *
 *     &#64;PostMapping("/{table}")
 *     public ResponseEntity&lt;?&gt; handlePost(
 *             &#64;PathVariable String table,
 *             &#64;RequestBody Map&lt;String, Object&gt; body,
 *             HttpServletRequest request) {
 *         return adapter.handle(body, request.getMethod(), table);
 *     }
 *
 *     &#64;GetMapping("/{table}")
 *     public ResponseEntity&lt;?&gt; handleGet(
 *             &#64;PathVariable String table,
 *             &#64;RequestParam Map&lt;String, String&gt; params,
 *             HttpServletRequest request) {
 *         Map&lt;String, Object&gt; body = new HashMap&lt;&gt;(params);
 *         return adapter.handle(body, request.getMethod(), table);
 *     }
 *
 *     &#64;PatchMapping("/{table}")
 *     public ResponseEntity&lt;?&gt; handlePatch(
 *             &#64;PathVariable String table,
 *             &#64;RequestBody Map&lt;String, Object&gt; body,
 *             HttpServletRequest request) {
 *         return adapter.handle(body, request.getMethod(), table);
 *     }
 *
 *     &#64;DeleteMapping("/{table}")
 *     public ResponseEntity&lt;?&gt; handleDelete(
 *             &#64;PathVariable String table,
 *             &#64;RequestBody(required = false) Map&lt;String, Object&gt; body,
 *             HttpServletRequest request) {
 *         return adapter.handle(body, request.getMethod(), table);
 *     }
 * }
 * </pre>
 */
public class SpringAdapter {

    private final BaseHandler handler;

    public SpringAdapter(AdapterOptions options) {
        this.handler = new BaseHandler(options);
    }

    /**
     * Handle a JSONQL request and return a framework-neutral response.
     *
     * <p>Returns a map that can be serialized by Spring's {@code ResponseEntity}
     * or {@code @ResponseBody}.</p>
     *
     * @param body       Parsed request body
     * @param httpMethod HTTP method string
     * @param pathName   URL path segment (table name)
     * @return Map with "status", "body" keys suitable for building a ResponseEntity
     */
    public ResponseResult handle(Map<String, Object> body, String httpMethod, String pathName) {
        try {
            Map<String, Object> data = handler.processRequest(body, httpMethod, pathName);
            return new ResponseResult(200, data);
        } catch (JsonQLValidationException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Validation Error");
            error.put("details", e.getErrors());
            return new ResponseResult(400, error);
        } catch (JsonQLException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return new ResponseResult(400, error);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return new ResponseResult(403, error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Internal Server Error");
            error.put("details", e.getMessage());
            return new ResponseResult(500, error);
        }
    }

    /**
     * Simple result wrapper holding an HTTP status code and response body.
     */
    public static class ResponseResult {
        public final int status;
        public final Map<String, Object> body;

        public ResponseResult(int status, Map<String, Object> body) {
            this.status = status;
            this.body = body;
        }
    }
}
