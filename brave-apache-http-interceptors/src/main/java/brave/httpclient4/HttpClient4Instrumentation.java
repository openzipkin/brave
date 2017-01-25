package brave.httpclient4;

import brave.ClientHandler;
import brave.Span;
import brave.Tracer;
import brave.parser.Parser;
import brave.parser.TagsParser;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import java.io.IOException;
import java.net.URI;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import zipkin.TraceKeys;

public final class HttpClient4Instrumentation {

  public static class Config extends ClientHandler.Config<HttpClientContext, HttpClientContext> {

    @Override protected Parser<HttpClientContext, String> spanNameParser() {
      return c -> c.getRequest().getRequestLine().getMethod();
    }

    @Override protected Parser<HttpClientContext, zipkin.Endpoint> responseAddressParser() {
      return new ServerAddressParser("");
    }

    @Override protected TagsParser<HttpClientContext> requestTagsParser() {
      return (req, span) -> span.tag(TraceKeys.HTTP_URL,
          req.getRequest().getRequestLine().getUri());
    }

    @Override protected TagsParser<HttpClientContext> responseTagsParser() {
      return (res, span) -> {
        int httpStatus = res.getResponse().getStatusLine().getStatusCode();
        if (httpStatus < 200 || httpStatus > 299) {
          span.tag(TraceKeys.HTTP_STATUS_CODE, String.valueOf(httpStatus));
        }
      };
    }
  }

  // all this still will be set via builder
  Tracer tracer = Tracer.newBuilder().build(); // add reporter etc
  ClientHandler<HttpClientContext, HttpClientContext> clientHandler =
      ClientHandler.create(new Config());
  TraceContext.Injector<HttpMessage> injector =
      Propagation.B3_STRING.injector(HttpMessage::setHeader);

  /**
   * Use this to explicitly control propagation.
   *
   * <p>For example, you'd create a new span for the request and response like so.
   * <pre>{@code
   *  Span clientSpan = spanFactory.start(parent);
   * }</pre>
   */
  public HttpRequestInterceptor requestInterceptor(final TraceContext parent) {
    return (request, context) -> {
      Span span = parent == null ? tracer.newTrace() : tracer.newChild(parent);
      try {
        clientHandler.handleSend(HttpClientContext.adapt(context), span);
        injector.inject(span.context(), request);
        context.setAttribute(ClientHandler.CONTEXT_KEY, span);
      } catch (RuntimeException e) {
        throw clientHandler.handleError(e, span);
      }
    };
  }

  public HttpResponseInterceptor responseInterceptor() {
    return (response, context) -> {
      Span span = (Span) context.getAttribute(ClientHandler.CONTEXT_KEY);
      if (span == null) return;
      clientHandler.handleReceive(HttpClientContext.adapt(context), span);
    };
  }

  public static void main(String... args) throws IOException {
    HttpClient4Instrumentation instrumentation = new HttpClient4Instrumentation();

    try (CloseableHttpClient client = HttpClients.custom()
        .disableAutomaticRetries()
        .addInterceptorFirst(instrumentation.requestInterceptor(null))
        .addInterceptorFirst(instrumentation.responseInterceptor())
        .build()) {
      client.execute(new HttpGet(URI.create("https://www.google.com"))).close();
    }
  }
}