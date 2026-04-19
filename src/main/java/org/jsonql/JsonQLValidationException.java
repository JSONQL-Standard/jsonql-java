package org.jsonql;

import java.util.List;
import org.jsonql.validator.JsonQLValidator;

/**
 * Thrown when a JSONQL query or mutation fails schema validation.
 *
 * <p>Carries the full list of {@link JsonQLValidator.ValidationError} instances so callers can
 * inspect individual problems.
 */
public class JsonQLValidationException extends JsonQLException {

    private final List<JsonQLValidator.ValidationError> errors;

    public JsonQLValidationException(String message, List<JsonQLValidator.ValidationError> errors) {
        super(message, errors != null && !errors.isEmpty() && errors.get(0).code != null
                ? errors.get(0).code : "VALIDATION_ERROR");
        this.errors = errors;
    }

    /** All individual validation errors. */
    public List<JsonQLValidator.ValidationError> getErrors() {
        return errors;
    }

    /** Convenience: return the first error (for fail-fast callers). */
    public JsonQLValidator.ValidationError getFirstError() {
        return errors != null && !errors.isEmpty() ? errors.get(0) : null;
    }
}
