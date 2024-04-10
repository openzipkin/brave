/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.beans;

import brave.baggage.CorrelationScopeConfig.SingleCorrelationField;
import brave.baggage.CorrelationScopeCustomizer;
import brave.baggage.CorrelationScopeDecorator;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;

import static brave.baggage.BaggageFields.SPAN_ID;
import static brave.baggage.BaggageFields.TRACE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CorrelationScopeDecoratorFactoryBeanTest {
  XmlBeans context;

  @AfterEach void close() {
    if (context != null) context.close();
  }

  @Test void builderRequired() {
    assertThrows(BeanCreationException.class, () -> {
      context = new XmlBeans(""
        + "<bean id=\"correlationDecorator\" class=\"brave.spring.beans.CorrelationScopeDecoratorFactoryBean\">\n"
        + "</bean>"
      );

      context.getBean("correlationDecorator", CorrelationScopeDecorator.class);
    });
  }

  @Test void builder() {
    context = new XmlBeans(""
      + "<bean id=\"correlationDecorator\" class=\"brave.spring.beans.CorrelationScopeDecoratorFactoryBean\">\n"
      + "  <property name=\"builder\">\n"
      + "    <bean class=\"brave.context.log4j12.MDCScopeDecorator\" factory-method=\"newBuilder\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    context.getBean("correlationDecorator", CorrelationScopeDecorator.class);
  }

  @Test void defaultFields() {
    context = new XmlBeans(""
      + "<bean id=\"correlationDecorator\" class=\"brave.spring.beans.CorrelationScopeDecoratorFactoryBean\">\n"
      + "  <property name=\"builder\">\n"
      + "    <bean class=\"brave.context.log4j12.MDCScopeDecorator\" factory-method=\"newBuilder\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("correlationDecorator", CorrelationScopeDecorator.class))
      .extracting("fields").asInstanceOf(InstanceOfAssertFactories.ARRAY)
      .containsExactly(
        SingleCorrelationField.create(TRACE_ID),
        SingleCorrelationField.create(SPAN_ID)
      );
  }

  @Test void configs() {
    context = new XmlBeans(""
      + "<util:constant id=\"traceId\" static-field=\"brave.baggage.BaggageFields.TRACE_ID\"/>\n"
      + "<bean id=\"correlationDecorator\" class=\"brave.spring.beans.CorrelationScopeDecoratorFactoryBean\">\n"
      + "  <property name=\"builder\">\n"
      + "    <bean class=\"brave.context.log4j12.MDCScopeDecorator\" factory-method=\"newBuilder\"/>\n"
      + "  </property>\n"
      + "  <property name=\"configs\">\n"
      + "    <list>\n"
      + "      <bean class=\"brave.spring.beans.SingleCorrelationFieldFactoryBean\">\n"
      + "        <property name=\"baggageField\" ref=\"traceId\"/>\n"
      + "        <property name=\"name\" value=\"X-B3-TraceId\"/>\n"
      + "      </bean>\n"
      + "    </list>\n"
      + "  </property>\n"
      + "</bean>"
    );

    ScopeDecorator decorator = context.getBean("correlationDecorator", ScopeDecorator.class);
    assertThat(decorator).extracting("field")
      .usingRecursiveComparison()
      .isEqualTo(
        SingleCorrelationField.newBuilder(TRACE_ID).name("X-B3-TraceId").build());
  }

  public static final CorrelationScopeCustomizer
    CUSTOMIZER_ONE = mock(CorrelationScopeCustomizer.class);
  public static final CorrelationScopeCustomizer
    CUSTOMIZER_TWO = mock(CorrelationScopeCustomizer.class);

  @Test void customizers() {
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
