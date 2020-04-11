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
import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.propagation.B3SinglePropagation;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import org.junit.After;
import org.junit.Test;
import zipkin2.reporter.Reporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TracingFactoryBeanTest {
  public static final Clock CLOCK = mock(Clock.class);
  public static final ErrorParser ERROR_PARSER = mock(ErrorParser.class);

  XmlBeans context;

  @After public void close() {
    if (context != null) context.close();
    Tracing current = Tracing.current();
    if (current != null) current.close();
  }

  @Test public void autoCloses() {
    context = new XmlBeans(""
      + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\"/>\n"
    );
    context.getBean("tracing", Tracing.class);

    assertThat(Tracing.current()).isNotNull();

    context.close();

    assertThat(Tracing.current()).isNull();

    context = null;
  }

  @Test public void localServiceName() {
    context = new XmlBeans(""
      + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
      + "  <property name=\"localServiceName\" value=\"brave-webmvc-example\"/>\n"
      + "</bean>"
    );

    assertThat(context.getBean("tracing", Tracing.class))
      .extracting("tracer.pendingSpans.defaultSpan.localServiceName")
      .isEqualTo("brave-webmvc-example");
  }

  @Test public void localEndpoint() {
    context = new XmlBeans(""
      + "<bean id=\"localEndpoint\" class=\"brave.spring.beans.EndpointFactoryBean\">\n"
      + "  <property name=\"serviceName\" value=\"brave-webmvc-example\"/>\n"
      + "  <property name=\"ip\" value=\"1.2.3.4\"/>\n"
      + "  <property name=\"port\" value=\"8080\"/>\n"
      + "</bean>"
      , ""
      + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
      + "  <property name=\"localEndpoint\" ref=\"localEndpoint\"/>\n"
      + "</bean>"
    );

    MutableSpan defaultSpan = new MutableSpan();
    defaultSpan.localServiceName("brave-webmvc-example");
    defaultSpan.localIp("1.2.3.4");
    defaultSpan.localPort(8080);

    assertThat(context.getBean("tracing", Tracing.class))
      .extracting("tracer.pendingSpans.defaultSpan")
      .isEqualTo(defaultSpan);
  }

  @Test public void endpoint() {
    context = new XmlBeans(""
      + "<bean id=\"endpoint\" class=\"brave.spring.beans.EndpointFactoryBean\">\n"
      + "  <property name=\"serviceName\" value=\"brave-webmvc-example\"/>\n"
      + "  <property name=\"ip\" value=\"1.2.3.4\"/>\n"
      + "  <property name=\"port\" value=\"8080\"/>\n"
      + "</bean>"
      , ""
      + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
      + "  <property name=\"endpoint\" ref=\"endpoint\"/>\n"
      + "</bean>"
    );

    MutableSpan defaultSpan = new MutableSpan();
    defaultSpan.localServiceName("brave-webmvc-example");
    defaultSpan.localIp("1.2.3.4");
    defaultSpan.localPort(8080);

    assertThat(context.getBean("tracing", Tracing.class))
      .extracting("tracer.pendingSpans.defaultSpan")
      .isEqualTo(defaultSpan);
  }

  @Test public void spanReporter() {
    context = new XmlBeans(""
      + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
      + "  <property name=\"spanReporter\">\n"
      + "    <util:constant static-field=\"zipkin2.reporter.Reporter.CONSOLE\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("tracing", Tracing.class))
      .extracting("tracer.spanHandler.delegate.spanReporter")
      .isEqualTo(Reporter.CONSOLE);
  }

  public static final FinishedSpanHandler FIREHOSE_HANDLER = mock(FinishedSpanHandler.class);

  @Test public void finishedSpanHandlers() {
    context = new XmlBeans(""
      + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
      + "  <property name=\"finishedSpanHandlers\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".FIREHOSE_HANDLER\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("tracing", Tracing.class))
      .extracting("tracer.spanHandler.delegate")
      .isEqualTo(FIREHOSE_HANDLER);
  }

  @Test public void clock() {
    context = new XmlBeans(""
      + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
      + "  <property name=\"clock\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".CLOCK\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("tracing", Tracing.class))
      .extracting("tracer.clock")
      .isEqualTo(CLOCK);
  }

  @Test public void errorParser() {
    context = new XmlBeans(""
      + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
      + "  <property name=\"errorParser\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".ERROR_PARSER\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("tracing", Tracing.class))
      .extracting("errorParser")
      .isEqualTo(ERROR_PARSER);
  }

  @Test public void sampler() {
    context = new XmlBeans(""
      + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
      + "  <property name=\"sampler\">\n"
      + "    <util:constant static-field=\"brave.sampler.Sampler.NEVER_SAMPLE\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("tracing", Tracing.class))
      .extracting("tracer.sampler")
      .isEqualTo(Sampler.NEVER_SAMPLE);
  }

  @Test public void currentTraceContext() {
    context = new XmlBeans(""
      + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
      + "  <property name=\"currentTraceContext\">\n"
      + "    <bean class=\"brave.spring.beans.CurrentTraceContextFactoryBean\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("tracing", Tracing.class))
      .extracting("tracer.currentTraceContext")
      .isInstanceOf(ThreadLocalCurrentTraceContext.class);
  }

  @Test public void propagationFactory() {
    context = new XmlBeans(""
      + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
      + "  <property name=\"propagationFactory\">\n"
      + "    <util:constant static-field=\"brave.propagation.B3SinglePropagation.FACTORY\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("tracing", Tracing.class).propagationFactory())
      .isSameAs(B3SinglePropagation.FACTORY);
  }

  @Test public void traceId128Bit() {
    context = new XmlBeans(""
      + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
      + "  <property name=\"traceId128Bit\" value=\"true\"/>\n"
      + "</bean>"
    );

    assertThat(context.getBean("tracing", Tracing.class))
      .extracting("tracer.traceId128Bit")
      .isEqualTo(true);
  }

  @Test public void supportsJoin() {
    context = new XmlBeans(""
      + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
      + "  <property name=\"supportsJoin\" value=\"true\"/>\n"
      + "</bean>"
    );

    assertThat(context.getBean("tracing", Tracing.class))
      .extracting("tracer.supportsJoin")
      .isEqualTo(true);
  }

  public static final TracingCustomizer CUSTOMIZER_ONE = mock(TracingCustomizer.class);
  public static final TracingCustomizer CUSTOMIZER_TWO = mock(TracingCustomizer.class);

  @Test public void customizers() {
    context = new XmlBeans(""
      + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
      + "  <property name=\"customizers\">\n"
      + "    <list>\n"
      + "      <util:constant static-field=\"" + getClass().getName() + ".CUSTOMIZER_ONE\"/>\n"
      + "      <util:constant static-field=\"" + getClass().getName() + ".CUSTOMIZER_TWO\"/>\n"
      + "    </list>\n"
      + "  </property>"
      + "</bean>"
    );

    context.getBean("tracing", Tracing.class);

    verify(CUSTOMIZER_ONE).customize(any(Tracing.Builder.class));
    verify(CUSTOMIZER_TWO).customize(any(Tracing.Builder.class));
  }
}
