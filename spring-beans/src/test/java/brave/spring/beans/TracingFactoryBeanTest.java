/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.beans;

import brave.Clock;
import brave.Tracing;
import brave.TracingCustomizer;
import brave.handler.SpanHandler;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TracingFactoryBeanTest {
  public static final Clock CLOCK = mock(Clock.class);
  XmlBeans context;

  @AfterEach void close() {
    if (context != null) context.close();
    Tracing current = Tracing.current();
    if (current != null) current.close();
  }

  @Test void autoCloses() {
    context = new XmlBeans(""
      + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\"/>\n"
    );
    context.getBean("tracing", Tracing.class);

    assertThat(Tracing.current()).isNotNull();

    context.close();

    assertThat(Tracing.current()).isNull();

    context = null;
  }

  @Test void localServiceName() {
    context = new XmlBeans(""
      + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
      + "  <property name=\"localServiceName\" value=\"brave-webmvc-example\"/>\n"
      + "</bean>"
    );

    assertThat(context.getBean("tracing", Tracing.class))
      .extracting("tracer.pendingSpans.defaultSpan.localServiceName")
      .isEqualTo("brave-webmvc-example");
  }

  public static final SpanHandler SPAN_HANDLER = mock(SpanHandler.class);

  @Test void spanHandlers() {
    context = new XmlBeans(""
      + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
      + "  <property name=\"spanHandlers\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".SPAN_HANDLER\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("tracing", Tracing.class))
      .extracting("tracer.spanHandler.delegate")
      .isEqualTo(SPAN_HANDLER);
  }

  @Test void clock() {
    context = new XmlBeans(""
      + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
      + "  <property name=\"clock\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".CLOCK\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("tracing", Tracing.class))
      .extracting("tracer.pendingSpans.clock")
      .isEqualTo(CLOCK);
  }

  @Test void sampler() {
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

  @Test void currentTraceContext() {
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

  @Test void traceId128Bit() {
    context = new XmlBeans(""
      + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
      + "  <property name=\"traceId128Bit\" value=\"true\"/>\n"
      + "</bean>"
    );

    assertThat(context.getBean("tracing", Tracing.class))
      .extracting("tracer.traceId128Bit")
      .isEqualTo(true);
  }

  @Test void supportsJoin() {
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

  @Test void customizers() {
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
