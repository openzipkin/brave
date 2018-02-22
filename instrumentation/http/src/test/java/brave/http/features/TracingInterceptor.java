package brave.http.features;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/** Example interceptor. Use the real deal brave-instrumentation-okhttp3 in real life */
final class TracingInterceptor implements Interceptor {
  final Tracer tracer;
  final HttpClientHandler<Request, Response> clientHandler;
  final TraceContext.Injector<Request.Builder> injector;

  TracingInterceptor(HttpTracing httpTracing) {
    tracer = httpTracing.tracing().tracer();
    clientHandler = HttpClientHandler.create(httpTracing, new OkHttpAdapter());
    injector = httpTracing.tracing().propagation().injector(Request.Builder::header);
  }

  @Override public Response intercept(Interceptor.Chain chain) throws IOException {
    Request.Builder builder = chain.request().newBuilder();
    Span span = clientHandler.handleSend(injector, builder, chain.request());
    Response response = null;
    Throwable error = null;
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
      return response = chain.proceed(builder.build());
    } catch (IOException | RuntimeException | Error e) {
      error = e;
      throw e;
    } finally {
      clientHandler.handleReceive(response, error, span);
    }
  }

  static final class OkHttpAdapter extends brave.http.HttpClientAdapter<Request, Response> {
    @Override @Nullable public String method(Request request) {
      return request.method();
    }

    @Override @Nullable public String path(Request request) {
      return request.url().encodedPath();
    }

    @Override @Nullable public String url(Request request) {
      return request.url().toString();
    }

    @Override @Nullable public String requestHeader(Request request, String name) {
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
