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

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContextCustomizer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CurrentTraceContextFactoryBeanTest {
  XmlBeans context;

  @After public void close() {
    if (context != null) context.close();
  }

  @Test public void scopeDecorators() {
    context = new XmlBeans(""
      + "<bean id=\"currentTraceContext\" class=\"brave.spring.beans.CurrentTraceContextFactoryBean\">\n"
      + "  <property name=\"scopeDecorators\">\n"
      + "    <list>\n"
      + "      <bean class=\"brave.propagation.StrictScopeDecorator\" factory-method=\"create\"/>\n"
      + "    </list>\n"
      + "  </property>"
      + "</bean>"
    );

    assertThat(context.getBean("currentTraceContext", CurrentTraceContext.class))
      .extracting("scopeDecorators")
      .asInstanceOf(InstanceOfAssertFactories.ARRAY)
      .isNotEmpty();
  }

  public static final CurrentTraceContextCustomizer
    CUSTOMIZER_ONE = mock(CurrentTraceContextCustomizer.class),
    CUSTOMIZER_TWO = mock(CurrentTraceContextCustomizer.class);

  @Test public void customizers() {
    context = new XmlBeans(""
      + "<bean id=\"currentTraceContext\" class=\"brave.spring.beans.CurrentTraceContextFactoryBean\">\n"
      + "  <property name=\"customizers\">\n"
      + "    <list>\n"
      + "      <util:constant static-field=\"" + getClass().getName() + ".CUSTOMIZER_ONE\"/>\n"
      + "      <util:constant static-field=\"" + getClass().getName() + ".CUSTOMIZER_TWO\"/>\n"
      + "    </list>\n"
      + "  </property>"
      + "</bean>"
    );

    context.getBean("currentTraceContext", CurrentTraceContext.class);

    verify(CUSTOMIZER_ONE).customize(any(CurrentTraceContext.Builder.class));
    verify(CUSTOMIZER_TWO).customize(any(CurrentTraceContext.Builder.class));
  }
}
