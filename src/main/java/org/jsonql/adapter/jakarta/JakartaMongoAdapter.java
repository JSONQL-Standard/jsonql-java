package org.jsonql.adapter.jakarta;

import java.util.HashMap;
import java.util.Map;
import org.jsonql.JsonQLException;
import org.jsonql.JsonQLValidationException;
import org.jsonql.adapter.MongoAdapter;
import org.jsonql.adapter.MongoAdapterOptions;

/**
 * Jakarta EE / JAX-RS adapter for JSONQL with MongoDB.
 *
 * <p>Wraps {@link MongoAdapter} to provide the same error-handling and {@link ResponseResult}
 * pattern as {@link JakartaAdapter}.
 *
 * <h3>Usage in a JAX-RS resource:</h3>
 *
 * <pre>
 * &#64;Path("/api")
 * &#64;Produces(MediaType.APPLICATION_JSON)
 * &#64;Consumes(MediaType.APPLICATION_JSON)
 * public class JsonQLMongoResource {
 *
 *     private final JakartaMongoAdapter adapter;
 *
 *     public JsonQLMongoResource(MongoDriverInterface driver) {
 *         MongoAdapterOptions opts = new MongoAdapterOptions()
 *                 .driver(driver);
 *         this.adapter = new JakartaMongoAdapter(opts);
 *     }
 *
 *     &#64;POST
 *     &#64;Path("/{collection}")
 *     public Response handlePost(
 *             &#64;PathParam("collection") String collection,
 *             Map&lt;String, Object&gt; body) {
 *         var result = adapter.handle(body, "POST", collection);
 *         return Response.status(result.status).entity(result.body).build();
 *     }
 * }
 * </pre>
 */
public class JakartaMongoAdapter {

    private final MongoAdapter handler;

    public JakartaMongoAdapter(MongoAdapterOptions options) {
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
    public JakartaAdapter.ResponseResult handle(
            Map<String, Object> body, String httpMethod, String pathName) {
        try {
            Map<String, Object> data = handler.processRequest(body, httpMethod, pathName);
            return new JakartaAdapter.ResponseResult(200, data);
        } catch (JsonQLValidationException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Validation Error");
            error.put("details", e.getErrors());
            return new JakartaAdapter.ResponseResult(400, error);
        } catch (JsonQLException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return new JakartaAdapter.ResponseResult(400, error);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            error.put("error_code", "PARSE_ERROR");
            return new JakartaAdapter.ResponseResult(400, error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Internal Server Error");
            error.put("details", e.getMessage());
            return new JakartaAdapter.ResponseResult(500, error);
        }
    }
}
