package com.github.kristofa.brave.spark;

import com.github.kristofa.brave.http.HttpServerRequest;
import spark.Request;

import java.net.URI;

/**
 * Created by 00013708 on 2017/3/10.
 */
public class SparkHttpServerRequest implements HttpServerRequest {
    private Request request;

    public SparkHttpServerRequest(Request request) {
        this.request = request;
    }


    @Override
    public String getHttpHeaderValue(String headerName) {
        return request.headers(headerName);
    }

    @Override
    public URI getUri() {
        return URI.create(this.request.uri());
    }

    @Override
    public String getHttpMethod() {
        return request.requestMethod();
    }
}
