package com.github.kristofa.brave.http;

import com.github.kristofa.brave.TagExtractor;
import zipkin.TraceKeys;

final class HttpRequestValueParserFactory implements TagExtractor.ValueParserFactory {
  @Override public TagExtractor.ValueParser<?> create(Class<?> type, String key) {
    if (!HttpRequest.class.isAssignableFrom(type)) return null;

    if (key.equals(TraceKeys.HTTP_HOST)) { // Can't switch on string since minimum JRE 6
      return new TagExtractor.ValueParser<HttpRequest>() {
        @Override public String parse(HttpRequest input) {
          return input.getUri().getHost();
        }
      };
    } else if (key.equals(TraceKeys.HTTP_METHOD)) {
      return new TagExtractor.ValueParser<HttpRequest>() {
        @Override public String parse(HttpRequest input) {
          return input.getHttpMethod();
        }
      };
    } else if (key.equals(TraceKeys.HTTP_PATH)) {
      return new TagExtractor.ValueParser<HttpRequest>() {
        @Override public String parse(HttpRequest input) {
          return input.getUri().getPath();
        }
      };
    } else if (key.equals(TraceKeys.HTTP_URL)) {
      return new TagExtractor.ValueParser<HttpRequest>() {
        @Override public String parse(HttpRequest input) {
          return input.getUri().toASCIIString();
        }
      };
    } else if (key.equals(TraceKeys.HTTP_REQUEST_SIZE)
        && HttpServerRequest.class.isAssignableFrom(type)) {
      return new TagExtractor.ValueParser<HttpServerRequest>() {
        @Override public String parse(HttpServerRequest input) {
          return input.getHttpHeaderValue("Content-Length");
        }
      };
    } else {
      return null;
    }
  }
}
