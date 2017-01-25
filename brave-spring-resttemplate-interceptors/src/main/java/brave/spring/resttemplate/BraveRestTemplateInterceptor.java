package brave.spring.resttemplate;

import brave.ClientHandler;
import brave.Span;
import brave.Tracer;
import brave.parser.Parser;
import brave.parser.TagsParser;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import zipkin.TraceKeys;

public final class BraveRestTemplateInterceptor implements ClientHttpRequestInterceptor {

  public static class Config extends ClientHandler.Config<HttpRequest, ClientHttpResponse> {

    @Override protected Parser<HttpRequest, String> spanNameParser() {
      return r -> r.getMethod().name();
    }

    @Override protected TagsParser<HttpRequest> requestTagsParser() {
      return (req, span) -> span.tag(TraceKeys.HTTP_URL, req.getURI().toString());
    }

    @Override protected TagsParser<ClientHttpResponse> responseTagsParser() {
      return (res, span) -> {
        try {
          int httpStatus = res.getRawStatusCode();
          if (httpStatus < 200 || httpStatus > 299) {
            span.tag(TraceKeys.HTTP_STATUS_CODE, String.valueOf(httpStatus));
          }
        } catch (IOException e) {
          // don't log a fake value on exception
        }
      };
    }
  }

  // all this still will be set via builder
  Tracer tracer = Tracer.newBuilder().build(); // add reporter etc
  ClientHandler<HttpRequest, ClientHttpResponse> clientHandler =
      ClientHandler.create(new BraveRestTemplateInterceptor.Config());
  TraceContext.Injector<HttpHeaders> injector = Propagation.B3_STRING.injector(HttpHeaders::set);

  @Override
  public ClientHttpResponse intercept(HttpRequest request, byte[] body,
      ClientHttpRequestExecution execution) throws IOException {
    // TODO: get current span
    Span span = tracer.newTrace();
    clientHandler.handleSend(request, span);
    injector.inject(span.context(), request.getHeaders());
    try {
      return clientHandler.handleReceive(execution.execute(request, body), span);
    } catch (RuntimeException e) {
      throw clientHandler.handleError(e, span);
    } catch (IOException e) {
      throw clientHandler.handleError(e, span);
    }
  }
}