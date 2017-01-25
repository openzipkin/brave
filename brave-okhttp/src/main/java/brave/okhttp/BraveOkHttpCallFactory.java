package brave.okhttp;

import brave.ClientHandler;
import brave.Span;
import brave.Tracer;
import brave.parser.Parser;
import brave.parser.TagsParser;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import zipkin.TraceKeys;

public class BraveOkHttpCallFactory implements Call.Factory {

  public static class Config extends ClientHandler.Config<Request, Response> {

    @Override protected Parser<Request, String> spanNameParser() {
      return Request::method;
    }

    @Override protected TagsParser<Request> requestTagsParser() {
      return (req, span) -> span.tag(TraceKeys.HTTP_URL, req.url().toString());
    }

    @Override protected TagsParser<Response> responseTagsParser() {
      return (res, span) -> {
        if (!res.isSuccessful()) {
          span.tag(TraceKeys.HTTP_STATUS_CODE, String.valueOf(res.code()));
        }
      };
    }
  }

  final Tracer tracer;
  final OkHttpClient ok;
  final ClientHandler<Request, Response> clientHandler;
  final TraceContext.Injector<Request.Builder> injector;

  BraveOkHttpCallFactory(Tracer tracer, OkHttpClient ok) {
    this.tracer = tracer;
    this.ok = ok;
    this.injector = Propagation.B3_STRING.injector(Request.Builder::addHeader);
    this.clientHandler = ClientHandler.create(new Config());
  }

  @Override
  public Call newCall(final Request request) {
    // TODO: get "current span"
    Span span = tracer.newTrace();
    return newCall(span, request);
  }

  public Call newCall(final Span span, final Request request) {
    if (span.isNoop()) return ok.newCall(request);
    OkHttpClient.Builder b = this.ok.newBuilder();
    b.interceptors().add(0, chain -> {
      clientHandler.handleSend(request, span);
      Request.Builder builder = request.newBuilder();
      injector.inject(span.context(), builder);
      try {
        return clientHandler.handleReceive(chain.proceed(builder.build()), span);
      } catch (IOException e) {
        throw clientHandler.handleError(e, span);
      } catch (RuntimeException e) {
        throw clientHandler.handleError(e, span);
      }
    });
    return b.build().newCall(request);
  }
}