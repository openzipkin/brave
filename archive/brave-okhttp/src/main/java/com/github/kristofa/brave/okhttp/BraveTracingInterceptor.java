package com.github.kristofa.brave.okhttp;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.InheritableServerClientAndLocalSpanState;
import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.LocalTracer;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.http.BraveHttpHeaders;
import com.twitter.zipkin.gen.Endpoint;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import zipkin.Constants;
import zipkin.TraceKeys;

import static com.github.kristofa.brave.IdConversion.convertToString;
import static com.github.kristofa.brave.http.BraveHttpHeaders.Sampled;
import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * This is an OkHttp interceptor which traces client requests. It should be applied as both an
 * {@link OkHttpClient#interceptors() application interceptor} and as a {@link
 * OkHttpClient#networkInterceptors() network interceptor}.
 *
 * <p>The implementation models the application request as a local span. Each network request will
 * be a child span. For example, if there's a redirect, there will be one for the application
 * request and two spans for the associated network requests.
 *
 * Trace identifiers of each network attempt are propagated to the server via headers prefixed with
 * `X-B3`. These spans are also reported out of band, with {@link zipkin.Constants#CLIENT_SEND} and
 * {@link zipkin.Constants#CLIENT_RECV} annotations, binary annotations (tags) like {@link
 * TraceKeys#HTTP_URL} and {@link zipkin.Constants#SERVER_ADDR the server's ip and port}.
 *
 * <h3>Configuration</h3>
 *
 * <p>Since this interceptor creates nested spans, you should use nesting-aware
 * span state like {@link InheritableServerClientAndLocalSpanState}. If using
 * asynchronous calls, you must also wrap the dispatcher's executor
 * service. Regardless, the interceptor must be registered as both an
 * application and network interceptor.
 *
 * <p>Here's how to add tracing to OkHttp:
 * <pre>{@code
 * brave = new Brave.Builder(new InheritableServerClientAndLocalSpanState(localEndpoint))..
 *
 * // The request dispatcher uses an executor service.. wrap it!
 * tracePropagatingExecutor = new BraveExecutorService(
 *   new Dispatcher().executorService(),
 *   brave.serverSpanThreadBinder()
 * );
 *
 * client = new OkHttpClient.Builder()
 *   .addInterceptor(tracingInterceptor)
 *   .addNetworkInterceptor(tracingInterceptor)
 *   .dispatcher(new Dispatcher(tracePropagatingExecutor));
 *   .build();
 * }</pre>
 *
 * @deprecated Replaced by {@code TracingInterceptor} from brave-instrumentation-okhttp3
 */
@Deprecated
public final class BraveTracingInterceptor implements Interceptor {
  public static BraveTracingInterceptor create(Brave brave) {
    return builder(brave).build();
  }

  /** Defaults to use {@link OkHttpParser} */
  public static Builder builder(Brave brave) {
    return new Builder(brave);
  }

  public static final class Builder {
    final Brave brave;
    String serverName = "";
    OkHttpParser parser = new OkHttpParser();

    Builder(Brave brave) { // intentionally hidden
      this.brave = checkNotNull(brave, "brave");
    }

    /**
     * Indicates the service name used for the {@link Constants#SERVER_ADDR server address}.Default
     * is empty string.
     *
     * <p>Setting this is not important when the server is instrumented with Zipkin. This is
     * important when the server is not instrumented with Zipkin. For example, if you are calling a
     * cloud service, you will see this name as a leaf in your service dependency graph.
     */
    public Builder serverName(String serverName) {
      this.serverName = checkNotNull(serverName, "serverName");
      return this;
    }

    /** Controls the metadata recorded in spans representing http operations. */
    public Builder parser(OkHttpParser parser) {
      this.parser = checkNotNull(parser, "parser");
      return this;
    }

    public BraveTracingInterceptor build() {
      return new BraveTracingInterceptor(this);
    }
  }

  final LocalTracer localTracer;
  final ClientTracer clientTracer;
  final OkHttpParser parser;
  final String serverName;

  BraveTracingInterceptor(Builder builder) {
    localTracer = builder.brave.localTracer();
    clientTracer = builder.brave.clientTracer();
    parser = builder.parser;
    serverName = builder.serverName;
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
    Connection connection = chain.connection();
    boolean applicationRequest = connection == null;

    Request request = chain.request();
    SpanId spanId;
    if (applicationRequest) {
      spanId = localTracer.startNewSpan("okhttp", parser.applicationSpanName(request));
    } else {
      spanId = clientTracer.startNewSpan(parser.networkSpanName(request));
    }

    if (spanId == null) { // trace was unsampled
      return applicationRequest
          ? chain.proceed(request)
          : chain.proceed(request.newBuilder().header(Sampled.getName(), "0").build());
    } else if (applicationRequest) {
      return traceApplicationRequest(chain, request);
    } else {
      Request tracedRequest = addTraceHeaders(request, spanId).build();
      return traceNetworkRequest(chain, tracedRequest);
    }
  }

  static Request.Builder addTraceHeaders(Request request, SpanId spanId) {
    Request.Builder tracedRequest = request.newBuilder();
    tracedRequest.header(BraveHttpHeaders.TraceId.getName(), spanId.traceIdString());
    tracedRequest.header(BraveHttpHeaders.SpanId.getName(), convertToString(spanId.spanId));
    if (spanId.nullableParentId() != null) {
      tracedRequest.header(BraveHttpHeaders.ParentSpanId.getName(),
          convertToString(spanId.parentId));
    }
    tracedRequest.header(BraveHttpHeaders.Sampled.getName(), "1");
    return tracedRequest;
  }

  /** We do not add trace headers to the application request, as it never leaves the process */
  Response traceApplicationRequest(Chain chain, Request request) throws IOException {
    try {
      return chain.proceed(request);
    } catch (IOException | RuntimeException | Error e) {
      // TODO: revisit https://github.com/openzipkin/openzipkin.github.io/issues/52
      String message = e.getMessage();
      if (message == null) message = e.getClass().getSimpleName();
      localTracer.submitBinaryAnnotation(Constants.ERROR, message);
      throw e;
    } finally {
      localTracer.finishSpan(); // span must be closed!
    }
  }

  Response traceNetworkRequest(Chain chain, Request request) throws IOException {
    appendToSpan(parser.networkRequestTags(request));
    try {
      clientTracer.setClientSent(serverAddress(chain.connection()));
      Response response = chain.proceed(request);
      appendToSpan(parser.networkResponseTags(response));
      return response;
    } catch (IOException | RuntimeException | Error e) {
      // TODO: revisit https://github.com/openzipkin/openzipkin.github.io/issues/52
      String message = e.getMessage();
      if (message == null) message = e.getClass().getSimpleName();
      clientTracer.submitBinaryAnnotation(Constants.ERROR, message);
      throw e;
    } finally {
      clientTracer.setClientReceived(); // span must be closed!
    }
  }

  void appendToSpan(List<KeyValueAnnotation> annotations) {
    for (int i = 0, length = annotations.size(); i < length; i++) {
      KeyValueAnnotation tag = annotations.get(i);
      clientTracer.submitBinaryAnnotation(tag.getKey(), tag.getValue());
    }
  }

  Endpoint serverAddress(Connection connection) {
    InetSocketAddress sa = connection.route().socketAddress();
    Endpoint.Builder builder = Endpoint.builder().serviceName(serverName).port(sa.getPort());
    byte[] address = sa.getAddress().getAddress();
    if (address.length == 4) {
      builder.ipv4(ByteBuffer.wrap(address).getInt());
    } else if (address.length == 16) {
      builder.ipv6(address);
    }
    return builder.build();
  }
}
