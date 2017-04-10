package com.github.kristofa.brave.sparkjava;

import com.github.kristofa.brave.http.HttpServerRequest;
import java.net.URI;
import javax.servlet.http.HttpServletRequest;
import spark.Request;

class SparkHttpServerRequest implements HttpServerRequest {
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
    HttpServletRequest httpServletRequest = request.raw();
    StringBuffer url = httpServletRequest.getRequestURL();
    if (httpServletRequest.getQueryString() != null && !httpServletRequest.getQueryString()
        .isEmpty()) {
      url.append('?').append(httpServletRequest.getQueryString());
    }
    return URI.create(url.toString());
  }

  @Override
  public String getHttpMethod() {
    return request.requestMethod();
  }
}
