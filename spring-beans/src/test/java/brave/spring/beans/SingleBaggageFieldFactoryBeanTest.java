/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.beans;

import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagationConfig;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SingleBaggageFieldFactoryBeanTest {
  XmlBeans context;

  @AfterEach void close() {
    if (context != null) context.close();
  }

  @Test void leastProperties() {
    context = new XmlBeans(""
      + "<bean id=\"userId\" class=\"brave.baggage.BaggageField\" factory-method=\"create\">\n"
      + "  <constructor-arg><value>userId</value></constructor-arg>\n"
      + "</bean>\n"
      + "<bean id=\"userIdBaggageConfig\" class=\"brave.spring.beans.SingleBaggageFieldFactoryBean\">\n"
      + "  <property name=\"field\" ref=\"userId\"/>\n"
      + "</bean>\n"
    );

    assertThat(context.getBean("userIdBaggageConfig", BaggagePropagationConfig.class))
      .usingRecursiveComparison()
      .isEqualTo(SingleBaggageField.local(BaggageField.create("userId")));
  }

  @Test void allProperties() {
    context = new XmlBeans(""
      + "<bean id=\"userId\" class=\"brave.baggage.BaggageField\" factory-method=\"create\">\n"
      + "  <constructor-arg><value>userId</value></constructor-arg>\n"
      + "</bean>\n"
      + "<bean id=\"userIdBaggageConfig\" class=\"brave.spring.beans.SingleBaggageFieldFactoryBean\">\n"
      + "  <property name=\"field\" ref=\"userId\"/>\n"
      + "  <property name=\"keyNames\">\n"
      + "    <list>\n"
      + "      <value>user-id</value>\n"
      + "    </list>\n"
      + "  </property>\n"
      + "</bean>\n"
    );

    assertThat(context.getBean("userIdBaggageConfig", BaggagePropagationConfig.class))
      .usingRecursiveComparison()
      .isEqualTo(SingleBaggageField.newBuilder(BaggageField.create("userId"))
        .addKeyName("user-id")
        .build());
  }
}
