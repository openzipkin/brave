package brave.okhttp3;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * This is a network-level interceptor, which creates a new span for each attempt. Note that this
 * does not work well for high traffic servers, as the span context can be lost when under backlog.
 * In cases like that, use {@link TracingCallFactory}.
 */
public final class TracingInterceptor implements Interceptor {
  static final Setter<Request.Builder, String> SETTER = new Setter<Request.Builder, String>() {
    @Override public void put(Request.Builder carrier, String key, String value) {
      carrier.header(key, value);
    }

    @Override public String toString() {
      return "Request.Builder::header";
    }
  };

  public static Interceptor create(Tracing tracing) {
    return create(HttpTracing.create(tracing));
  }

  public static Interceptor create(HttpTracing httpTracing) {
    return new TracingInterceptor(httpTracing);
  }

  final Tracer tracer;
  final String remoteServiceName;
  final HttpClientHandler<Request, Response> handler;
  final TraceContext.Injector<Request.Builder> injector;

  TracingInterceptor(HttpTracing httpTracing) {
    if (httpTracing == null) throw new NullPointerException("HttpTracing == null");
    tracer = httpTracing.tracing().tracer();
    remoteServiceName = httpTracing.serverName();
    handler = HttpClientHandler.create(httpTracing, new HttpAdapter());
    injector = httpTracing.tracing().propagation().injector(SETTER);
  }

  @Override public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    Request.Builder requestBuilder = request.newBuilder();

    Span span = handler.handleSend(injector, requestBuilder, request);
    parseRouteAddress(chain, span);

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

  static void parseRouteAddress(Chain chain, Span span) {
    if (span.isNoop()) return;
    Connection connection = chain.connection();
    if (connection == null) return;
    InetSocketAddress socketAddress = connection.route().socketAddress();
    span.remoteIpAndPort(socketAddress.getHostString(), socketAddress.getPort());
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
      return statusCodeAsInt(response);
    }

    @Override public int statusCodeAsInt(Response response) {
      return response.code();
    }
  }
}
