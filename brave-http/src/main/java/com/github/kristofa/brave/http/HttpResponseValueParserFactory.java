package com.github.kristofa.brave.http;

import com.github.kristofa.brave.TagExtractor;
import java.lang.reflect.Type;
import zipkin.TraceKeys;

final class HttpResponseValueParserFactory implements TagExtractor.ValueParserFactory {
  @Override public TagExtractor.ValueParser<?> create(Class<?> type, String key) {
    if (!HttpResponse.class.isAssignableFrom(type)) return null;

    if (key.equals(TraceKeys.HTTP_STATUS_CODE)) { // Can't switch on string since minimum JRE 6
      return new TagExtractor.ValueParser<HttpResponse>() {
        @Override public String parse(HttpResponse input) {
          int httpStatus = input.getHttpStatusCode();
          return httpStatus < 200 || httpStatus > 299 ? String.valueOf(httpStatus) : null;
        }
      };
    } else {
      return null;
    }
  }
}
