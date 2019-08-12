/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
import brave.http.HttpSampler;
import brave.http.HttpServerParser;
import brave.http.HttpTracing;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class HttpTracingFactoryBeanTest {

  public static Tracing TRACING = mock(Tracing.class);
  public static HttpClientParser CLIENT_PARSER = mock(HttpClientParser.class);
  public static HttpServerParser SERVER_PARSER = mock(HttpServerParser.class);

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

    assertThat(context.getBean("httpTracing", HttpTracing.class))
      .extracting("clientParser")
      .isEqualTo(CLIENT_PARSER);
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

    assertThat(context.getBean("httpTracing", HttpTracing.class))
      .extracting("serverParser")
      .isEqualTo(SERVER_PARSER);
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
      .isEqualTo(HttpSampler.NEVER_SAMPLE);
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

    assertThat(context.getBean("httpTracing", HttpTracing.class))
      .extracting("serverSampler")
      .isEqualTo(HttpSampler.NEVER_SAMPLE);
  }
}
