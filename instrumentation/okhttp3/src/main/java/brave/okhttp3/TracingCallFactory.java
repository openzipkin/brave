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
// This will be completely different after https://github.com/square/okhttp/issues/270
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
    handler = HttpClientHandler.create(httpTracing, new HttpAdapter());
    injector = httpTracing.tracing().propagation().injector(Request.Builder::addHeader);
    this.ok = ok;
  }

  @Override public Call newCall(Request request) {
    Span currentSpan = tracer.currentSpan();
    OkHttpClient.Builder b = ok.newBuilder();
    if (currentSpan != null) b.interceptors().add(0, new SetParentSpanInScope(currentSpan));
    b.networkInterceptors().add(0, new TracingNetworkInterceptor());
    // TODO: This can hide errors at the beginning of call.execute, such as invalid host!
    return b.build().newCall(request);
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
      Request request = chain.request();
      Request.Builder requestBuilder = request.newBuilder();

      Span span = handler.handleSend(injector, requestBuilder, request);
      parseServerAddress(chain.connection(), span);
      Response response = null;
      Throwable error = null;
      try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
        return response = chain.proceed(requestBuilder.build());
      } catch (IOException | RuntimeException | Error e) {
        error = e;
        throw e;
      } finally {
        handler.handleReceive(response, error, span);
      }
    }
  }

  /** This is different than default because the request does not hold the address */
  void parseServerAddress(Connection connection, Span span) {
    if (span.isNoop()) return;
    InetSocketAddress remoteAddress = connection.route().socketAddress();
    Endpoint.Builder builder = Endpoint.builder().serviceName(remoteServiceName);
    builder.parseIp(remoteAddress.getAddress());
    builder.port(remoteAddress.getPort());
    span.remoteEndpoint(builder.build());
  }

  static final class HttpAdapter extends brave.http.HttpClientAdapter<Request, Response> {
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
}
