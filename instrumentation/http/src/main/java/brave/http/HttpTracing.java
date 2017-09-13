package brave.http;

import brave.Tracing;
import com.google.auto.value.AutoValue;
import zipkin2.Endpoint;

@AutoValue
public abstract class HttpTracing {
  public static HttpTracing create(Tracing tracing) {
    return newBuilder(tracing).build();
  }

  public static Builder newBuilder(Tracing tracing) {
    return new AutoValue_HttpTracing.Builder()
        .tracing(tracing)
        .serverName("")
        .clientParser(new HttpClientParser())
        .serverParser(new HttpServerParser())
        .clientSampler(HttpSampler.TRACE_ID)
        .serverSampler(HttpSampler.TRACE_ID);
  }

  public abstract Tracing tracing();

  public abstract HttpClientParser clientParser();

  /**
   * Used by http clients to indicate the name of the destination service.
   *
   * Defaults to "", which will not show in the zipkin UI or end up in the dependency graph.
   *
   * <p>When present, a link from {@link Tracing.Builder#localServiceName(String)} to this name will
   * increment for each traced client call.
   *
   * <p>As this is endpoint-specific, it is typical to create a scoped instance of {@linkplain
   * HttpTracing} to assign this value.
   *
   * For example:
   * <pre>{@code
   * github = TracingHttpClientBuilder.create(httpTracing.serverName("github"));
   * }</pre>
   *
   * @see zipkin2.Constants#SERVER_ADDR
   * @see HttpClientAdapter#parseServerAddress(Object, Endpoint.Builder)
   * @see brave.Span#remoteEndpoint(Endpoint)
   */
  public abstract String serverName();

  /**
   * Scopes this component for a client of the indicated server.
   *
   * @see #serverName()
   */
  public HttpTracing clientOf(String serverName) {
    return toBuilder().serverName(serverName).build();
  }

  public abstract HttpServerParser serverParser();

  /**
   * Returns an overriding sampling decision for a new trace. Defaults to ignore the request and use
   * the {@link HttpSampler#TRACE_ID trace ID instead}.
   *
   * <p>This decision happens when a trace was not yet started in process. For example, you may be
   * making an http request as a part of booting your application. You may want to opt-out of
   * tracing client requests that did not originate from a server request.
   */
  public abstract HttpSampler clientSampler();

  /**
   * Returns an overriding sampling decision for a new trace. Defaults to ignore the request and use
   * the {@link HttpSampler#TRACE_ID trace ID instead}.
   *
   * <p>This decision happens when trace IDs were not in headers, or a sampling decision has not yet
   * been made. For example, if a trace is already in progress, this function is not called. You can
   * implement this to skip paths that you never want to trace.
   */
  public abstract HttpSampler serverSampler();

  public abstract Builder toBuilder();

  @AutoValue.Builder
  public static abstract class Builder {
    /** @see HttpTracing#tracing() */
    public abstract Builder tracing(Tracing tracing);

    /** @see HttpTracing#clientParser() */
    public abstract Builder clientParser(HttpClientParser clientParser);

    /** @see HttpTracing#serverParser() */
    public abstract Builder serverParser(HttpServerParser serverParser);

    /** @see HttpTracing#clientSampler() */
    public abstract Builder clientSampler(HttpSampler clientSampler);

    /** @see HttpTracing#serverSampler() */
    public abstract Builder serverSampler(HttpSampler serverSampler);

    public abstract HttpTracing build();

    abstract Builder serverName(String serverName);

    Builder() {
    }
  }

  HttpTracing() {
  }
}
