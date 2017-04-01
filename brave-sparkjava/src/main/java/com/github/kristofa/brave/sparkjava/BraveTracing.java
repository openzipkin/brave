package com.github.kristofa.brave.sparkjava;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.HttpServerRequestAdapter;
import com.github.kristofa.brave.http.HttpServerResponseAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.github.kristofa.brave.servlet.internal.MaybeAddClientAddressFromRequest;
import spark.ExceptionHandler;
import spark.Filter;
import spark.Request;
import spark.Response;
import zipkin.Constants;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

public final class BraveTracing {

  public static BraveTracing create(Brave brave) {
    return new BraveTracing.Builder(brave).build();
  }

  public static BraveTracing.Builder builder(Brave brave) {
    return new BraveTracing.Builder(brave);
  }

  public static final class Builder {
    final Brave brave;
    SpanNameProvider spanNameProvider = new DefaultSpanNameProvider();

    Builder(Brave brave) { // intentionally hidden
      this.brave = checkNotNull(brave, "brave");
    }

    public BraveTracing.Builder spanNameProvider(SpanNameProvider spanNameProvider) {
      this.spanNameProvider = checkNotNull(spanNameProvider, "spanNameProvider");
      return this;
    }

    public BraveTracing build() {
      return new BraveTracing(this);
    }
  }

  private final ServerRequestInterceptor requestInterceptor;
  private final ServerResponseInterceptor responseInterceptor;
  private final ServerSpanThreadBinder serverThreadBinder;
  private final SpanNameProvider spanNameProvider;
  private final ServerTracer serverTracer;
  private final MaybeAddClientAddressFromRequest maybeAddClientAddressFromRequest;

  BraveTracing(BraveTracing.Builder b) { // intentionally hidden
    this.requestInterceptor = b.brave.serverRequestInterceptor();
    this.responseInterceptor = b.brave.serverResponseInterceptor();
    this.serverThreadBinder = b.brave.serverSpanThreadBinder();
    this.spanNameProvider = b.spanNameProvider;
    this.serverTracer = b.brave.serverTracer();
    maybeAddClientAddressFromRequest = MaybeAddClientAddressFromRequest.create(b.brave);
  }

  public Filter before() {
    return new Filter() {
      @Override
      public void handle(Request request, Response response) throws Exception {
        requestInterceptor.handle(
            new HttpServerRequestAdapter(new SparkHttpServerRequest(request), spanNameProvider));
        maybeAddClientAddressFromRequest.accept(request.raw());
      }
    };
  }

  public Filter afterAfter() {
    return new Filter() {
      @Override
      public void handle(Request request, Response response) throws Exception {
        responseInterceptor.handle(
            new HttpServerResponseAdapter(new SparkHttpServerResponse(response)));
      }
    };
  }

  public ExceptionHandler exception(ExceptionHandler delegate) {
    return new ExceptionHandler() {
      @Override public void handle(Exception exception, Request request, Response response) {
        try {
          String message = exception.getMessage();
          if (message == null) message = exception.getClass().getSimpleName();
          serverTracer.submitBinaryAnnotation(Constants.ERROR, message);
        } finally {
          delegate.handle(exception, request, response);
        }
      }
    };
  }
}
