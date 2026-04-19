package org.jsonql;

import java.util.List;

/**
 * Configuration options for the JSONQL parser that control security and validation limits.
 *
 * <p>Usage:
 *
 * <pre>
 * JsonQLParserOptions options = new JsonQLParserOptions()
 *     .setMaxNestingDepth(3)
 *     .setMaxLimit(100)
 *     .setAllowedFields(List.of("id", "name", "email"));
 * </pre>
 */
public class JsonQLParserOptions {

    /** Maximum depth of nested includes (0 = unlimited) */
    private int maxNestingDepth = 0;

    /** Maximum value allowed for limit (0 = unlimited) */
    private int maxLimit = 0;

    /** Restrict which field names can appear in queries (null = all allowed) */
    private List<String> allowedFields;

    /** Restrict which relation names can be included (null = all allowed) */
    private List<String> allowedIncludes;

    public int getMaxNestingDepth() {
        return maxNestingDepth;
    }

    public JsonQLParserOptions setMaxNestingDepth(int maxNestingDepth) {
        this.maxNestingDepth = maxNestingDepth;
        return this;
    }

    public int getMaxLimit() {
        return maxLimit;
    }

    public JsonQLParserOptions setMaxLimit(int maxLimit) {
        this.maxLimit = maxLimit;
        return this;
    }

    public List<String> getAllowedFields() {
        return allowedFields;
    }

    public JsonQLParserOptions setAllowedFields(List<String> allowedFields) {
        this.allowedFields = allowedFields;
        return this;
    }

    public List<String> getAllowedIncludes() {
        return allowedIncludes;
    }

    public JsonQLParserOptions setAllowedIncludes(List<String> allowedIncludes) {
        this.allowedIncludes = allowedIncludes;
        return this;
    }
}
