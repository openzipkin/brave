package brave.okhttp3;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import zipkin.Endpoint;

/**
 * This internally adds an interceptor which ensures whatever current span exists is available via
 * {@link Tracer#currentSpan()}
 */
// NOTE: this is not an interceptor because the current span can get lost when there's a backlog.
public final class TracingCallFactory implements Call.Factory {

  public static Call.Factory create(Tracing tracing, OkHttpClient ok) {
    return create(HttpTracing.create(tracing), ok);
  }

  public static Call.Factory create(HttpTracing httpTracing, OkHttpClient ok) {
    return new TracingCallFactory(httpTracing, ok);
  }

  final Tracer tracer;
  final String remoteServiceName;
  final HttpClientHandler<Request, Response> handler;
  final TraceContext.Injector<Request.Builder> injector;
  final OkHttpClient ok;

  TracingCallFactory(HttpTracing httpTracing, OkHttpClient ok) {
    if (httpTracing == null) throw new NullPointerException("HttpTracing == null");
    if (ok == null) throw new NullPointerException("OkHttpClient == null");
    tracer = httpTracing.tracing().tracer();
    remoteServiceName = httpTracing.serverName();
    handler = new HttpClientHandler<>(new HttpAdapter(), httpTracing.clientParser());
    injector = httpTracing.tracing().propagation().injector(Request.Builder::addHeader);
    this.ok = ok;
  }

  @Override public Call newCall(Request request) {
    Span currentSpan = tracer.currentSpan();
    OkHttpClient.Builder b = ok.newBuilder();
    if (currentSpan != null) b.interceptors().add(0, new SetParentSpanInScope(currentSpan));
    b.networkInterceptors().add(0, new TracingNetworkInterceptor());
    return b.build().newCall(request);
  }

  static final class HttpAdapter extends brave.http.HttpAdapter<Request, Response> {
    @Override public String method(Request request) {
      return request.method();
    }

    @Override public String path(Request request) {
      return request.url().encodedPath();
    }

    @Override public String url(Request request) {
      return request.url().toString();
    }

    @Override public String requestHeader(Request request, String name) {
      return request.header(name);
    }

    @Override public Integer statusCode(Response response) {
      return response.code();
    }
  }

  /** In case a request is deferred due to a backlog, we re-apply the span that was in scope */
  class SetParentSpanInScope implements Interceptor {
    final Span previous;

    SetParentSpanInScope(Span previous) {
      this.previous = previous;
    }

    @Override public Response intercept(Chain chain) throws IOException {
      try (Tracer.SpanInScope ws = tracer.withSpanInScope(previous)) {
        return chain.proceed(chain.request());
      }
    }
  }

  class TracingNetworkInterceptor implements Interceptor {
    @Override public Response intercept(Chain chain) throws IOException {
      Span span = tracer.nextSpan();
      parseServerAddress(chain.connection(), span);
      Request request = chain.request();
      handler.handleSend(request, span);
      Request.Builder requestBuilder = request.newBuilder();
      injector.inject(span.context(), requestBuilder);
      try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
        return handler.handleReceive(chain.proceed(requestBuilder.build()), span);
      } catch (IOException | RuntimeException e) {
        handler.handleError(e, span);
        throw e;
      }
    }
  }

  void parseServerAddress(Connection connection, Span span) {
    if (span.isNoop()) return;
    InetSocketAddress remoteAddress = connection.route().socketAddress();
    Endpoint.Builder builder = Endpoint.builder().serviceName(remoteServiceName);
    builder.parseIp(remoteAddress.getAddress());
    builder.port(remoteAddress.getPort());
    span.remoteEndpoint(builder.build());
  }
}
