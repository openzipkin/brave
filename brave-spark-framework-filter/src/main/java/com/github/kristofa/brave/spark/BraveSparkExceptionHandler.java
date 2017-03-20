package com.github.kristofa.brave.spark;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.http.HttpServerResponseAdapter;
import spark.ExceptionHandler;
import spark.Request;
import spark.Response;
import zipkin.Constants;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Created by 00013708 on 2017/3/18.
 */
public class BraveSparkExceptionHandler implements ExceptionHandler {

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
            try {
                ServerTracer serverTracer = brave.serverTracer();
                if (serverTracer != null) {
                    serverTracer.submitBinaryAnnotation(Constants.ERROR, ex.getMessage());
                }
            } finally {
                brave.serverResponseInterceptor().handle(new HttpServerResponseAdapter(new SparkHttpServerResponse(response)));
            }
        } finally {
            exceptionHandler.handle(ex, request, response);
        }
    }
}
