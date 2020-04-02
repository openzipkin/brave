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

import brave.baggage.BaggageFields;
import brave.baggage.CorrelationScopeCustomizer;
import brave.baggage.CorrelationScopeDecorator;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.BeanCreationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CorrelationScopeDecoratorFactoryBeanTest {
  XmlBeans context;

  @After public void close() {
    if (context != null) context.close();
  }

  @Test(expected = BeanCreationException.class) public void builderRequired() {
    context = new XmlBeans(""
      + "<bean id=\"correlationDecorator\" class=\"brave.spring.beans.CorrelationScopeDecoratorFactoryBean\">\n"
      + "</bean>"
    );

    context.getBean("correlationDecorator", CorrelationScopeDecorator.class);
  }

  @Test public void builder() {
    context = new XmlBeans(""
      + "<bean id=\"correlationDecorator\" class=\"brave.spring.beans.CorrelationScopeDecoratorFactoryBean\">\n"
      + "  <property name=\"builder\">\n"
      + "    <bean class=\"brave.context.log4j12.MDCScopeDecorator\" factory-method=\"newBuilder\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    context.getBean("correlationDecorator", CorrelationScopeDecorator.class);
  }

  @Test public void defaultFields() {
    context = new XmlBeans(""
      + "<bean id=\"correlationDecorator\" class=\"brave.spring.beans.CorrelationScopeDecoratorFactoryBean\">\n"
      + "  <property name=\"builder\">\n"
      + "    <bean class=\"brave.context.log4j12.MDCScopeDecorator\" factory-method=\"newBuilder\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("correlationDecorator", CorrelationScopeDecorator.class))
      .extracting("fields").asInstanceOf(InstanceOfAssertFactories.ARRAY)
      .containsExactly(BaggageFields.TRACE_ID, BaggageFields.SPAN_ID);
  }

  @Test public void fields() {
    context = new XmlBeans(""
      + "<bean id=\"correlationDecorator\" class=\"brave.spring.beans.CorrelationScopeDecoratorFactoryBean\">\n"
      + "  <property name=\"builder\">\n"
      + "    <bean class=\"brave.context.log4j12.MDCScopeDecorator\" factory-method=\"newBuilder\"/>\n"
      + "  </property>\n"
      + "  <property name=\"fields\">\n"
      + "    <list>\n"
      + "      <util:constant static-field=\"brave.baggage.BaggageFields.TRACE_ID\"/>\n"
      + "      <util:constant static-field=\"brave.baggage.BaggageFields.SPAN_ID\"/>\n"
      + "    </list>\n"
      + "  </property>"
      + "</bean>"
    );

    assertThat(context.getBean("correlationDecorator", CorrelationScopeDecorator.class))
      .extracting("fields").asInstanceOf(InstanceOfAssertFactories.ARRAY)
      .containsExactly(BaggageFields.TRACE_ID, BaggageFields.SPAN_ID);
  }

  @Test public void mappedFields() {
    context = new XmlBeans(""
      + "<util:constant id=\"traceId\" static-field=\"brave.baggage.BaggageFields.TRACE_ID\"/>\n"
      + "<bean id=\"correlationDecorator\" class=\"brave.spring.beans.CorrelationScopeDecoratorFactoryBean\">\n"
      + "  <property name=\"builder\">\n"
      + "    <bean class=\"brave.context.log4j12.MDCScopeDecorator\" factory-method=\"newBuilder\"/>\n"
      + "  </property>\n"
      + "  <property name=\"mappedFields\">\n"
      + "    <list>\n"
      + "      <bean class=\"brave.spring.beans.MappedBaggageField\">\n"
      + "        <property name=\"field\" ref=\"traceId\"/>\n"
      + "        <property name=\"name\" value=\"X-B3-TraceId\"/>\n"
      + "      </bean>\n"
      + "    </list>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("correlationDecorator", CorrelationScopeDecorator.class))
      .extracting("field")
      .isEqualTo(BaggageFields.TRACE_ID);
  }

  public static final CorrelationScopeCustomizer
    CUSTOMIZER_ONE = mock(CorrelationScopeCustomizer.class);
  public static final CorrelationScopeCustomizer
    CUSTOMIZER_TWO = mock(CorrelationScopeCustomizer.class);

  @Test public void customizers() {
    context = new XmlBeans(""
      + "<bean id=\"correlationDecorator\" class=\"brave.spring.beans.CorrelationScopeDecoratorFactoryBean\">\n"
      + "  <property name=\"builder\">\n"
      + "    <bean class=\"brave.context.log4j12.MDCScopeDecorator\" factory-method=\"newBuilder\"/>\n"
      + "  </property>\n"
      + "  <property name=\"customizers\">\n"
      + "    <list>\n"
      + "      <util:constant static-field=\"" + getClass().getName() + ".CUSTOMIZER_ONE\"/>\n"
      + "      <util:constant static-field=\"" + getClass().getName() + ".CUSTOMIZER_TWO\"/>\n"
      + "    </list>\n"
      + "  </property>"
      + "</bean>"
    );

    context.getBean("correlationDecorator", CorrelationScopeDecorator.class);

    verify(CUSTOMIZER_ONE).customize(any(CorrelationScopeDecorator.Builder.class));
    verify(CUSTOMIZER_TWO).customize(any(CorrelationScopeDecorator.Builder.class));
  }
}
