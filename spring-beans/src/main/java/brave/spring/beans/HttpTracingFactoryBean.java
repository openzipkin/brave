package brave.spring.beans;

import brave.Tracing;
import brave.http.HttpClientParser;
import brave.http.HttpSampler;
import brave.http.HttpServerParser;
import brave.http.HttpTracing;
import org.springframework.beans.factory.FactoryBean;

/** Spring XML config does not support chained builders. This converts accordingly */
public class HttpTracingFactoryBean implements FactoryBean {

  Tracing tracing;
  HttpClientParser clientParser;
  HttpServerParser serverParser;
  HttpSampler clientSampler;
  HttpSampler serverSampler;

  @Override public HttpTracing getObject() {
    HttpTracing.Builder builder = HttpTracing.newBuilder(tracing);
    if (clientParser != null) builder.clientParser(clientParser);
    if (serverParser != null) builder.serverParser(serverParser);
    if (clientSampler != null) builder.clientSampler(clientSampler);
    if (serverSampler != null) builder.serverSampler(serverSampler);
    return builder.build();
  }

  @Override public Class<? extends HttpTracing> getObjectType() {
    return HttpTracing.class;
  }

  @Override public boolean isSingleton() {
    return true;
  }

  public void setTracing(Tracing tracing) {
    this.tracing = tracing;
  }

  public void setClientParser(HttpClientParser clientParser) {
    this.clientParser = clientParser;
  }

  public void setServerParser(HttpServerParser serverParser) {
    this.serverParser = serverParser;
  }

  public void setClientSampler(HttpSampler clientSampler) {
    this.clientSampler = clientSampler;
  }

  public void setServerSampler(HttpSampler serverSampler) {
    this.serverSampler = serverSampler;
  }
}
