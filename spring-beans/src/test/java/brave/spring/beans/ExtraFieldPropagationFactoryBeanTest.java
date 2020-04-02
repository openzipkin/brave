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
import brave.propagation.B3Propagation;
import brave.propagation.B3SinglePropagation;
import brave.propagation.ExtraFieldCustomizer;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.Propagation;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ExtraFieldPropagationFactoryBeanTest {
  XmlBeans context;

  @After public void close() {
    if (context != null) context.close();
  }

  @Test public void propagationFactory_default() {
    context = new XmlBeans(""
      + "<bean id=\"propagationFactory\" class=\"brave.spring.beans.ExtraFieldPropagationFactoryBean\"/>"
    );

    assertThat(context.getBean("propagationFactory", Propagation.Factory.class))
      .extracting("delegate")
      .isEqualTo(B3Propagation.FACTORY);
  }

  @Test public void propagationFactory() {
    context = new XmlBeans(""
      + "<bean id=\"propagationFactory\" class=\"brave.spring.beans.ExtraFieldPropagationFactoryBean\">\n"
      + "  <property name=\"propagationFactory\">\n"
      + "    <util:constant static-field=\""
      + B3SinglePropagation.class.getName()
      + ".FACTORY\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("propagationFactory", Propagation.Factory.class))
      .extracting("delegate")
      .isEqualTo(B3SinglePropagation.FACTORY);
  }

  @Test public void fields() {
    context = new XmlBeans(""
      + "<bean id=\"propagationFactory\" class=\"brave.spring.beans.ExtraFieldPropagationFactoryBean\">\n"
      + "  <property name=\"fields\">\n"
      + "    <list>\n"
      + "      <value>customer-id</value>\n"
      + "      <value>x-vcap-request-id</value>\n"
      + "    </list>\n"
      + "  </property>"
      + "</bean>"
    );

    assertThat(context.getBean("propagationFactory", Propagation.Factory.class))
      .extracting("delegate.extraFactory.fields")
      .asInstanceOf(InstanceOfAssertFactories.ARRAY)
      .usingFieldByFieldElementComparator()
      .containsExactly(
        BaggageField.create("customer-id"),
        BaggageField.create("x-vcap-request-id")
      );
  }

  public static final ExtraFieldCustomizer CUSTOMIZER_ONE = mock(ExtraFieldCustomizer.class);
  public static final ExtraFieldCustomizer CUSTOMIZER_TWO = mock(ExtraFieldCustomizer.class);

  @Test public void customizers() {
    context = new XmlBeans(""
      + "<bean id=\"propagationFactory\" class=\"brave.spring.beans.ExtraFieldPropagationFactoryBean\">\n"
      + "  <property name=\"customizers\">\n"
      + "    <list>\n"
      + "      <util:constant static-field=\"" + getClass().getName() + ".CUSTOMIZER_ONE\"/>\n"
      + "      <util:constant static-field=\"" + getClass().getName() + ".CUSTOMIZER_TWO\"/>\n"
      + "    </list>\n"
      + "  </property>"
      + "</bean>"
    );

    context.getBean("propagationFactory", Propagation.Factory.class);

    verify(CUSTOMIZER_ONE).customize(any(ExtraFieldPropagation.FactoryBuilder.class));
    verify(CUSTOMIZER_TWO).customize(any(ExtraFieldPropagation.FactoryBuilder.class));
  }
}
