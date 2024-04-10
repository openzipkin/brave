/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.beans;

import brave.baggage.BaggageFields;
import brave.baggage.CorrelationScopeConfig;
import brave.baggage.CorrelationScopeConfig.SingleCorrelationField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SingleCorrelationFieldFactoryBeanTest {
  XmlBeans context;

  @AfterEach void close() {
    if (context != null) context.close();
  }

  @Test void leastProperties() {
    context = new XmlBeans(""
      + "<util:constant id=\"traceId\" static-field=\"brave.baggage.BaggageFields.TRACE_ID\"/>\n"
      + "<bean id=\"traceIdCorrelationConfig\" class=\"brave.spring.beans.SingleCorrelationFieldFactoryBean\">\n"
      + "  <property name=\"baggageField\" ref=\"traceId\"/>\n"
      + "</bean>\n"
    );

    assertThat(context.getBean("traceIdCorrelationConfig", CorrelationScopeConfig.class))
      .usingRecursiveComparison()
      .isEqualTo(SingleCorrelationField.create(BaggageFields.TRACE_ID));
  }

  @Test void allProperties() {
    context = new XmlBeans(""
      + "<util:constant id=\"traceId\" static-field=\"brave.baggage.BaggageFields.TRACE_ID\"/>\n"
      + "<bean id=\"traceIdCorrelationConfig\" class=\"brave.spring.beans.SingleCorrelationFieldFactoryBean\">\n"
      + "  <property name=\"baggageField\" ref=\"traceId\"/>\n"
      + "  <property name=\"name\" value=\"X-B3-TraceId\"/>\n"
      + "  <property name=\"dirty\" value=\"true\"/>\n"
      + "  <property name=\"flushOnUpdate\" value=\"true\"/>\n"
      + "</bean>\n"
    );

    assertThat(context.getBean("traceIdCorrelationConfig", CorrelationScopeConfig.class))
      .usingRecursiveComparison()
      .isEqualTo(SingleCorrelationField.newBuilder(BaggageFields.TRACE_ID)
        .name("X-B3-TraceId")
        .dirty()
        .flushOnUpdate()
        .build());
  }
}
