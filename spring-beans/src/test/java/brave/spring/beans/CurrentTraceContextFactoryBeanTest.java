/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.beans;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContextCustomizer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CurrentTraceContextFactoryBeanTest {
  XmlBeans context;

  @AfterEach void close() {
    if (context != null) context.close();
  }

  @Test void scopeDecorators() {
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

  @Test void customizers() {
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
