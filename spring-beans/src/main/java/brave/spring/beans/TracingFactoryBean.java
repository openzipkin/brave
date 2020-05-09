/*
 * Copyright 2013-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.spring.beans;

import brave.Clock;
import brave.ErrorParser;
import brave.Tracing;
import brave.TracingCustomizer;
import brave.handler.SpanHandler;
import brave.propagation.CurrentTraceContext;
import brave.propagation.Propagation;
import brave.sampler.Sampler;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

/** Spring XML config does not support chained builders. This converts accordingly */
public class TracingFactoryBean extends AbstractFactoryBean {
  // Spring uses commons logging
  static final Log logger = LogFactory.getLog(TracingFactoryBean.class);

  String localServiceName;
  @Deprecated Object localEndpoint, endpoint; // don't pin zipkin class
  @Deprecated Object spanReporter; // don't pin zipkin class
  List<SpanHandler> spanHandlers = new ArrayList<>();
  Clock clock;
  Sampler sampler;
  @Deprecated ErrorParser errorParser;
  CurrentTraceContext currentTraceContext;
  Propagation.Factory propagationFactory;
  Boolean traceId128Bit;
  Boolean supportsJoin;
  List<TracingCustomizer> customizers;

  @Override protected Tracing createInstance() {
    Tracing.Builder builder = Tracing.newBuilder();
    if (localServiceName != null) builder.localServiceName(localServiceName);
    if (localEndpoint == null) localEndpoint = endpoint;
    if (localEndpoint != null) {
      builder.endpoint((Endpoint) localEndpoint);
    }
    if (spanReporter != null) {
      builder.spanReporter((Reporter<Span>) spanReporter);
    }
    for (SpanHandler spanHandler : spanHandlers) {
      builder.addSpanHandler(spanHandler);
    }
    if (errorParser != null) builder.errorParser(errorParser);
    if (clock != null) builder.clock(clock);
    if (sampler != null) builder.sampler(sampler);
    if (currentTraceContext != null) builder.currentTraceContext(currentTraceContext);
    if (propagationFactory != null) builder.propagationFactory(propagationFactory);
    if (traceId128Bit != null) builder.traceId128Bit(traceId128Bit);
    if (supportsJoin != null) builder.supportsJoin(supportsJoin);
    if (customizers != null) {
      for (TracingCustomizer customizer : customizers) customizer.customize(builder);
    }
    return builder.build();
  }

  @Override protected void destroyInstance(Object instance) {
    ((Tracing) instance).close();
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

  @Deprecated public void setLocalEndpoint(Object localEndpoint) {
    logger.warn("The property 'localEndpoint' will be removed in a future release.\n"
        + "Use the property 'localServiceName' instead");
    this.localEndpoint = localEndpoint;
  }

  @Deprecated public void setEndpoint(Object endpoint) {
    logger.warn("The property 'endpoint' will be removed in a future release.\n"
        + "Use the property 'localServiceName' instead");
    this.endpoint = endpoint;
  }

  @Deprecated public void setSpanReporter(Object spanReporter) {
    logger.warn("The property 'spanReporter' will be removed in a future release.\n"
        + "Add ZipkinSpanHandler the list property 'spanHandlers' instead");
    this.spanReporter = spanReporter;
  }

  // NOTE: we don't need to use the FinishedSpanHandler type as it extends SpanHandler
  @Deprecated public void setFinishedSpanHandlers(List<SpanHandler> finishedSpanHandlers) {
    logger.warn("The list property 'finishedSpanHandlers' will be removed in a future release.\n"
        + "Use the list property 'spanHandlers' instead");
    this.spanHandlers.addAll(finishedSpanHandlers);
  }

  public void setSpanHandlers(List<SpanHandler> spanHandlers) {
    this.spanHandlers.addAll(spanHandlers);
  }

  public void setClock(Clock clock) {
    this.clock = clock;
  }

  @Deprecated public void setErrorParser(ErrorParser errorParser) {
    logger.warn("The property 'errorParser' will be removed in a future release.\n"
        + "Add ZipkinSpanHandler with the 'errorTag' you want into list property 'spanHandlers'");
    this.errorParser = errorParser;
  }

  public void setSampler(Sampler sampler) {
    this.sampler = sampler;
  }

  public void setCurrentTraceContext(CurrentTraceContext currentTraceContext) {
    this.currentTraceContext = currentTraceContext;
  }

  public void setPropagationFactory(Propagation.Factory propagationFactory) {
    this.propagationFactory = propagationFactory;
  }

  public void setTraceId128Bit(boolean traceId128Bit) {
    this.traceId128Bit = traceId128Bit;
  }

  public void setSupportsJoin(Boolean supportsJoin) {
    this.supportsJoin = supportsJoin;
  }

  public void setCustomizers(List<TracingCustomizer> customizers) {
    this.customizers = customizers;
  }
}
