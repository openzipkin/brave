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
import brave.propagation.Propagation;
import brave.rpc.RpcRequestParser;
import brave.rpc.RpcResponseParser;
import brave.rpc.RpcTracing;
import brave.rpc.RpcTracingCustomizer;
import brave.sampler.SamplerFunctions;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class RpcTracingFactoryBeanTest {

  public static Tracing TRACING = mock(Tracing.class);
  public static RpcRequestParser REQUEST_PARSER = mock(RpcRequestParser.class);
  public static RpcResponseParser RESPONSE_PARSER = mock(RpcResponseParser.class);
  public static Propagation<String> PROPAGATION = mock(Propagation.class);

  XmlBeans context;

  @After public void close() {
    if (context != null) context.close();
  }

  @Test public void tracing() {
    context = new XmlBeans(""
      + "<bean id=\"rpcTracing\" class=\"brave.spring.beans.RpcTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("rpcTracing", RpcTracing.class))
      .extracting("tracing")
      .isEqualTo(TRACING);
  }

  @Test public void clientRequestParser() {
    context = new XmlBeans(""
      + "<bean id=\"httpTracing\" class=\"brave.spring.beans.RpcTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "  <property name=\"clientRequestParser\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".REQUEST_PARSER\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("httpTracing", RpcTracing.class))
      .extracting("clientRequestParser")
      .isEqualTo(REQUEST_PARSER);
  }

  @Test public void clientResponseParser() {
    context = new XmlBeans(""
      + "<bean id=\"httpTracing\" class=\"brave.spring.beans.RpcTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "  <property name=\"clientResponseParser\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".RESPONSE_PARSER\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("httpTracing", RpcTracing.class))
      .extracting("clientResponseParser")
      .isEqualTo(RESPONSE_PARSER);
  }

  @Test public void serverRequestParser() {
    context = new XmlBeans(""
      + "<bean id=\"httpTracing\" class=\"brave.spring.beans.RpcTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "  <property name=\"serverRequestParser\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".REQUEST_PARSER\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("httpTracing", RpcTracing.class))
      .extracting("serverRequestParser")
      .isEqualTo(REQUEST_PARSER);
  }

  @Test public void serverResponseParser() {
    context = new XmlBeans(""
      + "<bean id=\"httpTracing\" class=\"brave.spring.beans.RpcTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "  <property name=\"serverResponseParser\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".RESPONSE_PARSER\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("httpTracing", RpcTracing.class))
      .extracting("serverResponseParser")
      .isEqualTo(RESPONSE_PARSER);
  }

  @Test public void clientSampler() {
    context = new XmlBeans(""
      + "<bean id=\"rpcTracing\" class=\"brave.spring.beans.RpcTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "  <property name=\"clientSampler\">\n"
      + "    <bean class=\"brave.sampler.SamplerFunctions\" factory-method=\"neverSample\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("rpcTracing", RpcTracing.class).clientSampler())
      .isEqualTo(SamplerFunctions.neverSample());
  }

  @Test public void serverSampler() {
    context = new XmlBeans(""
      + "<bean id=\"rpcTracing\" class=\"brave.spring.beans.RpcTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "  <property name=\"serverSampler\">\n"
      + "    <bean class=\"brave.sampler.SamplerFunctions\" factory-method=\"neverSample\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("rpcTracing", RpcTracing.class).serverSampler())
      .isEqualTo(SamplerFunctions.neverSample());
  }

  @Test public void propagation() {
    context = new XmlBeans(""
      + "<bean id=\"rpcTracing\" class=\"brave.spring.beans.RpcTracingFactoryBean\">\n"
      + "  <property name=\"tracing\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".TRACING\"/>\n"
      + "  </property>\n"
      + "  <property name=\"propagation\">\n"
      + "    <util:constant static-field=\"" + getClass().getName() + ".PROPAGATION\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("rpcTracing", RpcTracing.class).propagation())
      .isEqualTo(PROPAGATION);
  }

  public static final RpcTracingCustomizer CUSTOMIZER_ONE = mock(RpcTracingCustomizer.class);
  public static final RpcTracingCustomizer CUSTOMIZER_TWO = mock(RpcTracingCustomizer.class);

  @Test public void customizers() {
    context = new XmlBeans(""
      + "<bean id=\"rpcTracing\" class=\"brave.spring.beans.RpcTracingFactoryBean\">\n"
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

    context.getBean("rpcTracing", RpcTracing.class);

    verify(CUSTOMIZER_ONE).customize(any(RpcTracing.Builder.class));
    verify(CUSTOMIZER_TWO).customize(any(RpcTracing.Builder.class));
  }
}
