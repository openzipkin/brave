package com.github.kristofa.brave.sparkjava;

import com.github.kristofa.brave.http.HttpResponse;
import spark.Response;

class SparkHttpServerResponse implements HttpResponse {

  private Response response;

  public SparkHttpServerResponse(Response response) {
    this.response = response;
  }

  @Override
  public int getHttpStatusCode() {
    return response.raw().getStatus();
  }
}
