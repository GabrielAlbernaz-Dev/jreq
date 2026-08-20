package com.jreq.request.application;

import com.jreq.shared.validation.Constraints;

/**
 * A user-safe, non-blocking issue found while importing a collection. Never contains
 * credential values, tokens, or file contents — only request names and feature types.
 */
public record ImportWarning(String message) {
    public ImportWarning {
        message = Constraints.requiredText(message, "message", "A warning message is required.");
    }
}
