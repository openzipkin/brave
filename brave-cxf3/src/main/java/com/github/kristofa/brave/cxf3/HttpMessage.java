package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.http.HttpClientRequest;
import com.github.kristofa.brave.http.HttpResponse;
import com.github.kristofa.brave.http.HttpServerRequest;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.cxf.message.Message;

class HttpMessage {
  static final class ClientRequest extends HttpMessage implements HttpClientRequest {
    ClientRequest(Message message) {
      super(message);
    }

    @Override
    public void addHeader(String header, String value) {
      getHeaders(message).put(header, Arrays.asList(value));
    }

    @Override
    public URI getUri() {
      return URI.create((String) message.getExchange().get(Message.ENDPOINT_ADDRESS));
    }
  }

  static final class ServerRequest extends HttpMessage implements HttpServerRequest {
    ServerRequest(Message message) {
      super(message);
    }

    @Override
    public String getHttpHeaderValue(String headerName) {
      List<String> values = getHeaders(message).get(headerName);
      if (values != null && !values.isEmpty()) {
        return values.get(0);
      }
      return null;
    }

    @Override
    public URI getUri() {
      return URI.create((String) message.get(Message.REQUEST_URL));
    }
  }

  static final class Response extends HttpMessage implements HttpResponse {

    Response(Message message) {
      super(message);
    }

    @Override
    public int getHttpStatusCode() {
      Integer code = (Integer) message.get(Message.RESPONSE_CODE);
      if (code != null) {
        return code.intValue();
      } else {
        // Correct assumption?
        return 200;
      }
    }
  }

  protected final Message message;

  HttpMessage(Message message) {
    this.message = message;
  }

  Map<String, List<String>> getHeaders(Message message) {
    Map<String, List<String>> headers =
        (Map<String, List<String>>) message.get(Message.PROTOCOL_HEADERS);
    if (headers == null) {
      headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      message.put(Message.PROTOCOL_HEADERS, headers);
    }
    return headers;
  }

  public String getHttpMethod() {
    return (String) message.get(Message.HTTP_REQUEST_METHOD);
  }
}