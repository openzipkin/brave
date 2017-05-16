package com.github.kristofa.brave.spark;

import com.github.kristofa.brave.http.HttpResponse;
import spark.Response;

/**
 * Created by 00013708 on 2017/3/10.
 */
public class SparkHttpServerResponse implements HttpResponse {

    private Response response;

    public SparkHttpServerResponse(Response response) {
        this.response = response;
    }

    @Override
    public int getHttpStatusCode() {
        return 200;
    }

}
