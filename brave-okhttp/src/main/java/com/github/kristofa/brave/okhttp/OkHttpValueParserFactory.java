package com.github.kristofa.brave.okhttp;

import com.github.kristofa.brave.TagExtractor.ValueParser;
import com.github.kristofa.brave.TagExtractor.ValueParserFactory;
import java.io.IOException;
import okhttp3.Request;
import okhttp3.Response;
import zipkin.TraceKeys;

public final class OkHttpValueParserFactory implements ValueParserFactory {

  @Override public ValueParser<?> create(Class<?> type, String key) {
    if (type == Request.class) {
      switch (key) { // Switch on string is ok since OkHttp is minimum JRE 7 anyway
        case TraceKeys.HTTP_HOST:
          return new ValueParser<Request>() {
            @Override public String parse(Request input) {
              return input.url().host();
            }
          };
        case TraceKeys.HTTP_METHOD:
          return new ValueParser<Request>() {
            @Override public String parse(Request input) {
              return input.method();
            }
          };
        case TraceKeys.HTTP_PATH:
          return new ValueParser<Request>() {
            @Override public String parse(Request input) {
              return input.url().encodedPath();
            }
          };
        case TraceKeys.HTTP_URL:
          return new ValueParser<Request>() {
            @Override public String parse(Request input) {
              return input.url().toString();
            }
          };
        case TraceKeys.HTTP_REQUEST_SIZE:
          return new ValueParser<Request>() {
            @Override public String parse(Request input) {
              if (input.body() == null) return null;
              try {
                long result = input.body().contentLength();
                return result != -1 ? String.valueOf(result) : null;
              } catch (IOException e) {
                return null;
              }
            }
          };
      }
    } else if (type == Response.class) {
      if (key.equals(TraceKeys.HTTP_STATUS_CODE)) {
        return new ValueParser<Response>() {
          @Override public String parse(Response input) {
            return input.isSuccessful() ? null : String.valueOf(input.code());
          }
        };
      } else if (key.equals(TraceKeys.HTTP_RESPONSE_SIZE)) {
        return new ValueParser<Response>() {
          @Override public String parse(Response input) {
            long result = input.body().contentLength();
            return result != -1 ? String.valueOf(result) : null;
          }
        };
      }
    }
    return null;
  }
}