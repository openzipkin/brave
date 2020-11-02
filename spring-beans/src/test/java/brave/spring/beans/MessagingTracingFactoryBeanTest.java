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
import brave.messaging.MessagingTracing;
import brave.messaging.MessagingTracingCustomizer;
import brave.propagation.Propagation;
import brave.sampler.SamplerFunctions;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class MessagingTracingFactoryBeanTest {

  public static Tracing TRACING = mock(Tracing.class);
  public static Propagation<String> PROPAGATION = mock(Propagation.class);

  XmlBeans context;

  @After public void close() {
    if (context != null) context.close();
  }

  @Test public void tracing() {
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

  @Test public void producerSampler() {
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

  @Test public void consumerSampler() {
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

  @Test public void propagation() {
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

  @Test public void customizers() {
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
