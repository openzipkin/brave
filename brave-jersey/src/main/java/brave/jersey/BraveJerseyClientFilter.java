package brave.jersey;

import brave.ClientHandler;
import brave.Span;
import brave.Tracer;
import brave.parser.Parser;
import brave.parser.TagsParser;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;
import javax.inject.Singleton;
import javax.ws.rs.core.MultivaluedMap;
import zipkin.TraceKeys;

@Singleton
public class BraveJerseyClientFilter extends ClientFilter {

  public static class Config extends ClientHandler.Config<ClientRequest, ClientResponse> {

    @Override protected Parser<ClientRequest, String> spanNameParser() {
      return ClientRequest::getMethod;
    }

    @Override protected TagsParser<ClientRequest> requestTagsParser() {
      return (req, span) -> span.tag(TraceKeys.HTTP_URL, req.getURI().toString());
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
  TraceContext.Injector<MultivaluedMap> injector =
      Propagation.B3_STRING.injector(MultivaluedMap::putSingle);

  @Override
  public ClientResponse handle(final ClientRequest request) throws ClientHandlerException {
    // TODO: get current span
    Span span = tracer.newTrace();
    clientHandler.handleSend(request, span);
    injector.inject(span.context(), request.getHeaders());
    try {
      return clientHandler.handleReceive(getNext().handle(request), span);
    } catch (ClientHandlerException e) {
      throw clientHandler.handleError(e, span);
    }
  }
}