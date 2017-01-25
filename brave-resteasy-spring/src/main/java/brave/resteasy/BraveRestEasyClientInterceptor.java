package brave.resteasy;

import brave.ClientHandler;
import brave.Span;
import brave.Tracer;
import brave.parser.Parser;
import brave.parser.TagsParser;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import javax.ws.rs.ext.Provider;
import org.jboss.resteasy.annotations.interception.ClientInterceptor;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.spi.interception.ClientExecutionContext;
import org.jboss.resteasy.spi.interception.ClientExecutionInterceptor;
import org.springframework.stereotype.Component;
import zipkin.TraceKeys;

@Component
@Provider
@ClientInterceptor
public class BraveRestEasyClientInterceptor implements ClientExecutionInterceptor {

  public static class Config extends ClientHandler.Config<ClientRequest, ClientResponse> {

    @Override protected Parser<ClientRequest, String> spanNameParser() {
      return ClientRequest::getHttpMethod;
    }

    @Override protected TagsParser<ClientRequest> requestTagsParser() {
      return (req, span) -> {
        try {
          span.tag(TraceKeys.HTTP_URL, req.getUri().toString());
        } catch (Exception e) {
        }
      };
    }

    @Override protected TagsParser<ClientResponse> responseTagsParser() {
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
  ClientHandler<ClientRequest, ClientResponse> clientHandler = ClientHandler.create(new Config());
  TraceContext.Injector<ClientRequest> injector =
      Propagation.B3_STRING.injector(ClientRequest::header);

  @Override
  public ClientResponse<?> execute(final ClientExecutionContext ctx) throws Exception {
    final ClientRequest request = ctx.getRequest();
    // TODO: get current span
    Span span = tracer.newTrace();
    clientHandler.handleSend(request, span);
    injector.inject(span.context(), request);
    try {
      return clientHandler.handleReceive(ctx.proceed(), span);
    } catch (Exception e) {
      throw clientHandler.handleError(e, span);
    }
  }
}