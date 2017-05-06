package brave.http;

import brave.Tracing;
import com.google.auto.value.AutoValue;
import zipkin.Endpoint;

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
        .serverParser(new HttpServerParser());
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
   * github = TracingHttpClientBuilder.create(
   *   httpTracing.toBuilder().serverName("github").build()
   * ).build();
   * }</pre>
   *
   * @see zipkin.Constants#SERVER_ADDR
   * @see brave.Span#remoteEndpoint(Endpoint)
   */
  public abstract String serverName();

  public abstract HttpServerParser serverParser();

  public abstract Builder toBuilder();

  @AutoValue.Builder
  public static abstract class Builder {
    public abstract Builder tracing(Tracing tracing);

    public abstract Builder clientParser(HttpClientParser clientParser);

    public abstract Builder serverName(String serverName);

    public abstract Builder serverParser(HttpServerParser serverParser);

    public abstract HttpTracing build();

    Builder() {
    }
  }

  HttpTracing() {
  }
}
