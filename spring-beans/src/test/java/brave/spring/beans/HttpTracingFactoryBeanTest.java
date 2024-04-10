/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.beans;

import brave.Tracing;
import brave.http.HttpRequestParser;
import brave.http.HttpResponseParser;
import brave.http.HttpTracing;
import brave.http.HttpTracingCustomizer;
import brave.propagation.Propagation;
import brave.sampler.SamplerFunctions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class HttpTracingFactoryBeanTest {

  public static Tracing TRACING = mock(Tracing.class);
  public static HttpRequestParser REQUEST_PARSER = mock(HttpRequestParser.class);
  public static HttpResponseParser RESPONSE_PARSER = mock(HttpResponseParser.class);
  public static Propagation<String> PROPAGATION = mock(Propagation.class);

  XmlBeans context;

  @AfterEach void close() {
    if (context != null) context.close();
  }

  @Test void tracing() {
    context = new XmlBeans(""
      + "<bean id=\"httpTracing\" class=\"brave.spring.beans.HttpTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("httpTracing", HttpTracing.class))
      .extracting("tracing")
      .isEqualTo(TRACING);
  }

  @Test void clientRequestParser() {
    context = new XmlBeans(""
      + "<bean id=\"httpTracing\" class=\"brave.spring.beans.HttpTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "  <property name=\"clientRequestParser\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".REQUEST_PARSER\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("httpTracing", HttpTracing.class))
      .extracting("clientRequestParser")
      .isEqualTo(REQUEST_PARSER);
  }

  @Test void clientResponseParser() {
    context = new XmlBeans(""
      + "<bean id=\"httpTracing\" class=\"brave.spring.beans.HttpTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "  <property name=\"clientResponseParser\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".RESPONSE_PARSER\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("httpTracing", HttpTracing.class))
      .extracting("clientResponseParser")
      .isEqualTo(RESPONSE_PARSER);
  }

  @Test void serverRequestParser() {
    context = new XmlBeans(""
      + "<bean id=\"httpTracing\" class=\"brave.spring.beans.HttpTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "  <property name=\"serverRequestParser\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".REQUEST_PARSER\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("httpTracing", HttpTracing.class))
      .extracting("serverRequestParser")
      .isEqualTo(REQUEST_PARSER);
  }

  @Test void serverResponseParser() {
    context = new XmlBeans(""
      + "<bean id=\"httpTracing\" class=\"brave.spring.beans.HttpTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "  <property name=\"serverResponseParser\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".RESPONSE_PARSER\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("httpTracing", HttpTracing.class))
      .extracting("serverResponseParser")
      .isEqualTo(RESPONSE_PARSER);
  }

  @Test void clientRequestSampler() {
    context = new XmlBeans(""
      + "<bean id=\"httpTracing\" class=\"brave.spring.beans.HttpTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "  <property name=\"clientSampler\">\n"
      + "    <bean class=\"brave.sampler.SamplerFunctions\" factory-method=\"neverSample\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("httpTracing", HttpTracing.class).clientRequestSampler())
      .isEqualTo(SamplerFunctions.neverSample());
  }

  @Test void serverRequestSampler() {
    context = new XmlBeans(""
      + "<bean id=\"httpTracing\" class=\"brave.spring.beans.HttpTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "  <property name=\"serverSampler\">\n"
      + "    <bean class=\"brave.sampler.SamplerFunctions\" factory-method=\"neverSample\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("httpTracing", HttpTracing.class).serverRequestSampler())
      .isEqualTo(SamplerFunctions.neverSample());
  }

  @Test void propagation() {
    context = new XmlBeans(""
      + "<bean id=\"httpTracing\" class=\"brave.spring.beans.HttpTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "  <property name=\"propagation\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".PROPAGATION\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("httpTracing", HttpTracing.class).propagation())
      .isEqualTo(PROPAGATION);
  }

  public static final HttpTracingCustomizer CUSTOMIZER_ONE = mock(HttpTracingCustomizer.class);
  public static final HttpTracingCustomizer CUSTOMIZER_TWO = mock(HttpTracingCustomizer.class);

  @Test void customizers() {
    context = new XmlBeans(""
      + "<bean id=\"httpTracing\" class=\"brave.spring.beans.HttpTracingFactoryBean\">\n"
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

    context.getBean("httpTracing", HttpTracing.class);

    verify(CUSTOMIZER_ONE).customize(any(HttpTracing.Builder.class));
    verify(CUSTOMIZER_TWO).customize(any(HttpTracing.Builder.class));
  }
}
