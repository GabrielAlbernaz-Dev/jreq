package com.jreq.request.domain;

import com.jreq.shared.validation.Constraints;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record RequestBody(RequestBodyType type, String content, String contentType) {
    public RequestBody {
        Objects.requireNonNull(type, "type");
        content = Objects.requireNonNull(content, "content");
        contentType = Objects.requireNonNull(contentType, "contentType");
        if (type == RequestBodyType.NONE) {
            Constraints.requireArgument(
                    content.isEmpty() && contentType.isEmpty(),
                    "A NONE body cannot contain data");
        } else {
            contentType = Constraints.requiredText(
                    contentType, "contentType", "A request body requires a content type");
        }
    }

    public static RequestBody none() {
        return new RequestBody(RequestBodyType.NONE, "", "");
    }

    public static RequestBody json(String content) {
        return new RequestBody(RequestBodyType.JSON, content, "application/json");
    }

    public static RequestBody rawText(String content) {
        return new RequestBody(RequestBodyType.RAW_TEXT, content, "text/plain; charset=UTF-8");
    }

    public byte[] bytes() {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    public boolean isPresent() {
        return type != RequestBodyType.NONE;
    }
}
