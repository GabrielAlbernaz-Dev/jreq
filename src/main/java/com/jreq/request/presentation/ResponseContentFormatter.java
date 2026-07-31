package com.jreq.request.presentation;

import java.util.Optional;

interface ResponseContentFormatter {
    ResponseBodyFormat format();

    boolean supports(String mediaType);

    boolean looksLike(String body);

    Optional<String> prettyPrint(String body);
}
