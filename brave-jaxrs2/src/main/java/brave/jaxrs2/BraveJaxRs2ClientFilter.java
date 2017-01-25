package brave.jaxrs2;

import brave.ClientHandler;
import brave.ServerHandler;
import brave.Span;
import brave.Tracer;
import brave.parser.Parser;
import brave.parser.TagsParser;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import java.io.IOException;
import javax.annotation.Priority;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import zipkin.TraceKeys;

@Provider
@Priority(0)
public class BraveJaxRs2ClientFilter implements ClientRequestFilter, ClientResponseFilter {

  public static class Config
      extends ClientHandler.Config<ClientRequestContext, ClientResponseContext> {

    @Override protected Parser<ClientRequestContext, String> spanNameParser() {
      return ClientRequestContext::getMethod;
    }

    @Override protected TagsParser<ClientRequestContext> requestTagsParser() {
      return (req, span) -> span.tag(TraceKeys.HTTP_URL, req.getUri().toString());
    }

    @Override protected TagsParser<ClientResponseContext> responseTagsParser() {
      return (res, span) -> {
        int httpStatus = res.getStatus();
        if (httpStatus < 200 || httpStatus > 299) {
          span.tag(TraceKeys.HTTP_STATUS_CODE, String.valueOf(httpStatus));
        }
      };
    }
  }

  // all this still will be set via builder
  Tracer tracer = Tracer.newBuilder().build(); // add reporter etc
  ClientHandler<ClientRequestContext, ClientResponseContext> clientHandler =
      ClientHandler.create(new Config());
  TraceContext.Injector<MultivaluedMap> injector =
      Propagation.B3_STRING.injector(MultivaluedMap::putSingle);

  @Override
  public void filter(ClientRequestContext request) throws IOException {
    // TODO: get current span
    Span span = tracer.newTrace();
    request.setProperty(ClientHandler.CONTEXT_KEY, span);
    clientHandler.handleSend(request, span);
    injector.inject(span.context(), request.getHeaders());
  }

  @Override
  public void filter(ClientRequestContext request, ClientResponseContext response)
      throws IOException {
    Span span = (Span) request.getProperty(ServerHandler.CONTEXT_KEY);
    clientHandler.handleReceive(response, span);
  }
}