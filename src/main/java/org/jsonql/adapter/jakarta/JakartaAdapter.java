package org.jsonql.adapter.jakarta;

import java.util.HashMap;
import java.util.Map;
import org.jsonql.JsonQLException;
import org.jsonql.JsonQLValidationException;
import org.jsonql.adapter.AdapterOptions;
import org.jsonql.adapter.BaseHandler;

/**
 * Jakarta EE / JAX-RS adapter for JSONQL.
 *
 * <p>Usage in a JAX-RS resource:
 *
 * <pre>
 * &#64;Path("/api")
 * &#64;Produces(MediaType.APPLICATION_JSON)
 * &#64;Consumes(MediaType.APPLICATION_JSON)
 * public class JsonQLResource {
 *
 *     private final JakartaAdapter adapter;
 *
 *     public JsonQLResource() {
 *         AdapterOptions opts = new AdapterOptions()
 *             .dialect("postgres")
 *             .schema(schema)
 *             .connectionSupplier(() -> dataSource.getConnection());
 *         this.adapter = new JakartaAdapter(opts);
 *     }
 *
 *     &#64;POST
 *     &#64;Path("/{table}")
 *     public Response handlePost(&#64;PathParam("table") String table, Map&lt;String, Object&gt; body) {
 *         return adapter.handle(body, "POST", table);
 *     }
 *
 *     &#64;GET
 *     &#64;Path("/{table}")
 *     public Response handleGet(&#64;PathParam("table") String table, &#64;Context UriInfo uriInfo) {
 *         Map&lt;String, Object&gt; params = new HashMap&lt;&gt;();
 *         uriInfo.getQueryParameters().forEach((k, v) -> params.put(k, v.get(0)));
 *         return adapter.handle(params, "GET", table);
 *     }
 *
 *     &#64;PATCH
 *     &#64;Path("/{table}")
 *     public Response handlePatch(&#64;PathParam("table") String table, Map&lt;String, Object&gt; body) {
 *         return adapter.handle(body, "PATCH", table);
 *     }
 *
 *     &#64;DELETE
 *     &#64;Path("/{table}")
 *     public Response handleDelete(&#64;PathParam("table") String table, Map&lt;String, Object&gt; body) {
 *         return adapter.handle(body, "DELETE", table);
 *     }
 * }
 * </pre>
 */
public class JakartaAdapter {

    private final BaseHandler handler;

    public JakartaAdapter(AdapterOptions options) {
        this.handler = new BaseHandler(options);
    }

    /**
     * Handle a JSONQL request and return a framework-neutral response.
     *
     * @param body Parsed request body
     * @param httpMethod HTTP method string
     * @param pathName URL path segment (table name)
     * @return ResponseResult with status code and body
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

    /** Simple result wrapper holding an HTTP status code and response body. */
    public static class ResponseResult {
        public final int status;
        public final Map<String, Object> body;

        public ResponseResult(int status, Map<String, Object> body) {
            this.status = status;
            this.body = body;
        }
    }
}
