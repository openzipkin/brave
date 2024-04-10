/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.beans;

import brave.Tracing;
import brave.messaging.MessagingTracing;
import brave.messaging.MessagingTracingCustomizer;
import brave.propagation.Propagation;
import brave.sampler.SamplerFunctions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MessagingTracingFactoryBeanTest {

  public static Tracing TRACING = mock(Tracing.class);
  public static Propagation<String> PROPAGATION = mock(Propagation.class);

  XmlBeans context;

  @AfterEach void close() {
    if (context != null) context.close();
  }

  @Test void tracing() {
    context = new XmlBeans(""
      + "<bean id=\"messagingTracing\" class=\"brave.spring.beans.MessagingTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("messagingTracing", MessagingTracing.class))
      .extracting("tracing")
      .isEqualTo(TRACING);
  }

  @Test void producerSampler() {
    context = new XmlBeans(""
      + "<bean id=\"messagingTracing\" class=\"brave.spring.beans.MessagingTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "  <property name=\"producerSampler\">\n"
      + "    <bean class=\"brave.sampler.SamplerFunctions\" factory-method=\"neverSample\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("messagingTracing", MessagingTracing.class).producerSampler())
      .isEqualTo(SamplerFunctions.neverSample());
  }

  @Test void consumerSampler() {
    context = new XmlBeans(""
      + "<bean id=\"messagingTracing\" class=\"brave.spring.beans.MessagingTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "  <property name=\"consumerSampler\">\n"
      + "    <bean class=\"brave.sampler.SamplerFunctions\" factory-method=\"neverSample\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("messagingTracing", MessagingTracing.class).consumerSampler())
      .isEqualTo(SamplerFunctions.neverSample());
  }

  @Test void propagation() {
    context = new XmlBeans(""
      + "<bean id=\"messagingTracing\" class=\"brave.spring.beans.MessagingTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "  <property name=\"propagation\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".PROPAGATION\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("messagingTracing", MessagingTracing.class).propagation())
      .isEqualTo(PROPAGATION);
  }

  public static final MessagingTracingCustomizer CUSTOMIZER_ONE =
    mock(MessagingTracingCustomizer.class);
  public static final MessagingTracingCustomizer CUSTOMIZER_TWO =
    mock(MessagingTracingCustomizer.class);

  @Test void customizers() {
    context = new XmlBeans(""
      + "<bean id=\"messagingTracing\" class=\"brave.spring.beans.MessagingTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "  <property name=\"customizers\">\n"
      + "    <list>\n"
      + "      <util:constant static-field=\"" + getClass().getName() + ".CUSTOMIZER_ONE\"/>\n"
      + "      <util:constant static-field=\"" + getClass().getName() + ".CUSTOMIZER_TWO\"/>\n"
      + "    </list>\n"
      + "  </property>"
      + "</bean>"
    );

    context.getBean("messagingTracing", MessagingTracing.class);

    verify(CUSTOMIZER_ONE).customize(any(MessagingTracing.Builder.class));
    verify(CUSTOMIZER_TWO).customize(any(MessagingTracing.Builder.class));
  }
}
