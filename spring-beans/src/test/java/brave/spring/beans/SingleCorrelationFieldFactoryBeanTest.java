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
import brave.baggage.CorrelationScopeConfig;
import brave.baggage.CorrelationScopeConfig.SingleCorrelationField;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SingleCorrelationFieldFactoryBeanTest {
  XmlBeans context;

  @After public void close() {
    if (context != null) context.close();
  }

  @Test public void leastProperties() {
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

  @Test public void allProperties() {
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
