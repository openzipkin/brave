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

import brave.Tracing;
import brave.http.HttpClientParser;
import brave.http.HttpRequestParser;
import brave.http.HttpResponseParser;
import brave.http.HttpSampler;
import brave.http.HttpServerParser;
import brave.http.HttpTracing;
import brave.http.HttpTracingCustomizer;
import brave.propagation.Propagation;
import brave.sampler.SamplerFunctions;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class HttpTracingFactoryBeanTest {

  public static Tracing TRACING = mock(Tracing.class);
  public static HttpClientParser CLIENT_PARSER = mock(HttpClientParser.class);
  public static HttpServerParser SERVER_PARSER = mock(HttpServerParser.class);
  public static HttpRequestParser REQUEST_PARSER = mock(HttpRequestParser.class);
  public static HttpResponseParser RESPONSE_PARSER = mock(HttpResponseParser.class);
  public static Propagation<String> PROPAGATION = mock(Propagation.class);

  XmlBeans context;

  @After public void close() {
    if (context != null) context.close();
  }

  @Test public void tracing() {
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

  @Test public void clientParser() {
    context = new XmlBeans(""
      + "<bean id=\"httpTracing\" class=\"brave.spring.beans.HttpTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "  <property name=\"clientParser\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".CLIENT_PARSER\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("httpTracing", HttpTracing.class).clientParser())
      .isEqualTo(CLIENT_PARSER);
  }

  @Test public void clientRequestParser() {
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

  @Test public void clientResponseParser() {
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

  @Test public void serverParser() {
    context = new XmlBeans(""
      + "<bean id=\"httpTracing\" class=\"brave.spring.beans.HttpTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "  <property name=\"serverParser\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".SERVER_PARSER\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("httpTracing", HttpTracing.class).serverParser())
      .isEqualTo(SERVER_PARSER);
  }

  @Test public void serverRequestParser() {
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

  @Test public void serverResponseParser() {
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

  @Test public void clientSampler() {
    context = new XmlBeans(""
      + "<bean id=\"httpTracing\" class=\"brave.spring.beans.HttpTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "  <property name=\"clientSampler\">\n"
      + "    <util:constant static-field=\"brave.http.HttpSampler.NEVER_SAMPLE\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("httpTracing", HttpTracing.class))
      .extracting("clientSampler")
      .usingRecursiveComparison()
      .isEqualTo(HttpSampler.NEVER_SAMPLE);
  }

  @Test public void clientRequestSampler() {
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

  @Test public void serverSampler() {
    context = new XmlBeans(""
      + "<bean id=\"httpTracing\" class=\"brave.spring.beans.HttpTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "  <property name=\"serverSampler\">\n"
      + "    <util:constant static-field=\"brave.http.HttpSampler.NEVER_SAMPLE\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("httpTracing", HttpTracing.class).serverSampler())
      .isEqualTo(HttpSampler.NEVER_SAMPLE);
  }

  @Test public void serverRequestSampler() {
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

  @Test public void propagation() {
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

  @Test public void customizers() {
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
