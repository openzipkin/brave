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

import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationCustomizer;
import brave.propagation.B3Propagation;
import brave.propagation.B3SinglePropagation;
import brave.propagation.Propagation;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class BaggagePropagationFactoryBeanTest {
  XmlBeans context;

  @After public void close() {
    if (context != null) context.close();
  }

  @Test public void propagationFactory_default() {
    context = new XmlBeans(""
      + "<bean id=\"propagationFactory\" class=\"brave.spring.beans.BaggagePropagationFactoryBean\"/>\n"
    );

    assertThat(context.getBean("propagationFactory", Propagation.Factory.class))
      .isEqualTo(B3Propagation.FACTORY);
  }

  @Test public void propagationFactory_noFields() {
    context = new XmlBeans(""
      + "<bean id=\"propagationFactory\" class=\"brave.spring.beans.BaggagePropagationFactoryBean\">\n"
      + "  <property name=\"propagationFactory\">\n"
      + "    <util:constant static-field=\"brave.propagation.B3SinglePropagation.FACTORY\"/>\n"
      + "  </property>\n"
      + "</bean>\n"
    );

    assertThat(context.getBean("propagationFactory", Propagation.Factory.class))
      .isEqualTo(B3SinglePropagation.FACTORY);
  }

  @Test public void configs() {
    context = new XmlBeans(""
      + "<bean id=\"userIdBaggageField\" class=\"brave.baggage.BaggageField\" factory-method=\"create\">\n"
      + "  <constructor-arg value=\"userId\" />\n"
      + "</bean>\n"
      + "<bean id=\"propagationFactory\" class=\"brave.spring.beans.BaggagePropagationFactoryBean\">\n"
      + "  <property name=\"configs\">\n"
      + "    <list>\n"
      + "      <bean class=\"brave.spring.beans.SingleBaggageFieldFactoryBean\">\n"
      + "        <property name=\"field\" ref=\"userIdBaggageField\"/>\n"
      + "        <property name=\"keyNames\">\n"
      + "          <list>\n"
      + "            <value>baggage_user_id</value>\n"
      + "            <value>baggage-user-id</value>\n"
      + "          </list>\n"
      + "        </property>\n"
      + "      </bean>\n"
      + "    </list>\n"
      + "  </property>\n"
      + "</bean>"
    );

    Propagation<String> propagation =
      context.getBean("propagationFactory", Propagation.Factory.class).get();

    assertThat(BaggagePropagation.allKeyNames(propagation)).endsWith(
      "baggage_user_id",
      "baggage-user-id"
    );
  }

  /** Spring is graceful about a single field being substitutable for a list of size one */
  @Test public void configs_no_list() {
    context = new XmlBeans(""
      + "<bean id=\"userIdBaggageField\" class=\"brave.baggage.BaggageField\" factory-method=\"create\">\n"
      + "  <constructor-arg value=\"userId\" />\n"
      + "</bean>\n"
      + "<bean id=\"propagationFactory\" class=\"brave.spring.beans.BaggagePropagationFactoryBean\">\n"
      + "  <property name=\"configs\">\n"
      + "    <bean class=\"brave.spring.beans.SingleBaggageFieldFactoryBean\">\n"
      + "      <property name=\"field\" ref=\"userIdBaggageField\" />\n"
      + "      <property name=\"keyNames\" value=\"userid\" />\n"
      + "    </bean>\n"
      + "  </property>\n"
      + "</bean>"
    );

    Propagation<String> propagation =
      context.getBean("propagationFactory", Propagation.Factory.class).get();

    assertThat(BaggagePropagation.allKeyNames(propagation))
      .endsWith("userid");
  }

  @Test public void propagationFactory() {
    context = new XmlBeans(""
      + "<bean id=\"userIdBaggageField\" class=\"brave.baggage.BaggageField\" factory-method=\"create\">\n"
      + "  <constructor-arg value=\"userId\" />\n"
      + "</bean>\n"
      + "<bean id=\"propagationFactory\" class=\"brave.spring.beans.BaggagePropagationFactoryBean\">\n"
      + "  <property name=\"propagationFactory\">\n"
      + "    <util:constant static-field=\"brave.propagation.B3SinglePropagation.FACTORY\"/>\n"
      + "  </property>\n"
      + "  <property name=\"configs\">\n"
      + "    <list>\n"
      + "      <bean class=\"brave.spring.beans.SingleBaggageFieldFactoryBean\">\n"
      + "        <property name=\"field\" ref=\"userIdBaggageField\"/>\n"
      + "      </bean>\n"
      + "    </list>\n"
      + "  </property>\n"
      + "</bean>\n"
    );

    assertThat(context.getBean("propagationFactory", Propagation.Factory.class))
      .extracting("delegate")
      .isEqualTo(B3SinglePropagation.FACTORY);
  }

  public static final BaggagePropagationCustomizer
    CUSTOMIZER_ONE = mock(BaggagePropagationCustomizer.class);
  public static final BaggagePropagationCustomizer
    CUSTOMIZER_TWO = mock(BaggagePropagationCustomizer.class);

  @Test public void customizers() {
    context = new XmlBeans(""
      + "<bean id=\"propagationFactory\" class=\"brave.spring.beans.BaggagePropagationFactoryBean\">\n"
      + "  <property name=\"customizers\">\n"
      + "    <list>\n"
      + "      <util:constant static-field=\"" + getClass().getName() + ".CUSTOMIZER_ONE\"/>\n"
      + "      <util:constant static-field=\"" + getClass().getName() + ".CUSTOMIZER_TWO\"/>\n"
      + "    </list>\n"
      + "  </property>\n"
      + "</bean>\n"
    );

    context.getBean("propagationFactory", Propagation.Factory.class);

    verify(CUSTOMIZER_ONE).customize(any(BaggagePropagation.FactoryBuilder.class));
    verify(CUSTOMIZER_TWO).customize(any(BaggagePropagation.FactoryBuilder.class));
  }
}
