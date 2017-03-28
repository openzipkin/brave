package com.github.kristofa.brave.spark;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerTracer;
import spark.ExceptionHandler;
import spark.Request;
import spark.Response;
import spark.utils.StringUtils;
import zipkin.Constants;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

public final class BraveSparkExceptionHandler implements ExceptionHandler {

    private final Brave brave;

    private final ExceptionHandler exceptionHandler;

    public static BraveSparkExceptionHandler create(Brave brave, ExceptionHandler exceptionHandler) {
        checkNotNull(brave, "brave");
        checkNotNull(brave, "exceptionHandler");
        return new BraveSparkExceptionHandler(brave, exceptionHandler);
    }

    private BraveSparkExceptionHandler(Brave brave, ExceptionHandler exceptionHandler) {
        this.brave = brave;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void handle(Exception ex, Request request, Response response) {
        try {
            ServerTracer serverTracer = brave.serverTracer();
            StringBuilder sb = new StringBuilder();
            if (StringUtils.isNotEmpty(ex.getMessage())) {
                sb.append(ex.getMessage()).append("\n");
            }
            if (StringUtils.isNotEmpty(response.body())) {
                sb.append(response.body()).append("\n");
            }
            if (sb.length() == 0) {
                sb.append("exception happens no exception msg nor response body.");
            }
            serverTracer.submitBinaryAnnotation(Constants.ERROR, sb.toString());
        } finally {
            exceptionHandler.handle(ex, request, response);
        }
    }
}
