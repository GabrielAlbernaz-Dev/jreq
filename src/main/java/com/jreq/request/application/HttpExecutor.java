package com.jreq.request.application;

import com.jreq.request.domain.HttpRequestDefinition;

import java.util.concurrent.CompletableFuture;

public interface HttpExecutor {
    CompletableFuture<HttpResponseResult> execute(HttpRequestDefinition request);
}
