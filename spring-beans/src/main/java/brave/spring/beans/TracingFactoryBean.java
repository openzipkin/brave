package brave.spring.beans;

import brave.Clock;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.sampler.Sampler;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import zipkin.Endpoint;
import zipkin.reporter.Reporter;

/** Spring XML config does not support chained builders. This converts accordingly */
public class TracingFactoryBean extends AbstractFactoryBean<Tracing> {

  String localServiceName;
  Endpoint localEndpoint;
  Reporter<zipkin.Span> reporter;
  Clock clock;
  Sampler sampler;
  CurrentTraceContext currentTraceContext;
  Boolean traceId128Bit;

  @Override protected Tracing createInstance() throws Exception {
    Tracing.Builder builder = Tracing.newBuilder();
    if (localServiceName != null) builder.localServiceName(localServiceName);
    if (localEndpoint != null) builder.localEndpoint(localEndpoint);
    if (reporter != null) builder.reporter(reporter);
    if (clock != null) builder.clock(clock);
    if (sampler != null) builder.sampler(sampler);
    if (currentTraceContext != null) builder.currentTraceContext(currentTraceContext);
    if (traceId128Bit != null) builder.traceId128Bit(traceId128Bit);
    return builder.build();
  }

  @Override protected void destroyInstance(Tracing instance) throws Exception {
    instance.close();
  }

  @Override public Class<? extends Tracing> getObjectType() {
    return Tracing.class;
  }

  @Override public boolean isSingleton() {
    return true;
  }

  public void setLocalServiceName(String localServiceName) {
    this.localServiceName = localServiceName;
  }

  public void setLocalEndpoint(Endpoint localEndpoint) {
    this.localEndpoint = localEndpoint;
  }

  public void setReporter(Reporter<zipkin.Span> reporter) {
    this.reporter = reporter;
  }

  public void setClock(Clock clock) {
    this.clock = clock;
  }

  public void setSampler(Sampler sampler) {
    this.sampler = sampler;
  }

  public void setCurrentTraceContext(CurrentTraceContext currentTraceContext) {
    this.currentTraceContext = currentTraceContext;
  }

  public void setTraceId128Bit(boolean traceId128Bit) {
    this.traceId128Bit = traceId128Bit;
  }
}
