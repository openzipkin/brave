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

import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagationConfig;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SingleBaggageFieldFactoryBeanTest {
  XmlBeans context;

  @After public void close() {
    if (context != null) context.close();
  }

  @Test public void leastProperties() {
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

  @Test public void allProperties() {
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
