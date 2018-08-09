package brave.spring.beans;

import brave.propagation.CurrentTraceContext;
import brave.propagation.StrictScopeDecorator;
import java.util.List;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CurrentTraceContextFactoryBeanTest {
  XmlBeans context;

  @After public void close() {
    if (context != null) context.close();
  }

  @Test public void scopeDecorators() {
    context = new XmlBeans(""
        + "<bean id=\"currentTraceContext\" class=\"brave.spring.beans.CurrentTraceContextFactoryBean\">\n"
        + "  <property name=\"scopeDecorators\">\n"
        + "    <bean class=\"brave.propagation.StrictScopeDecorator\" factory-method=\"create\"/>\n"
        + "  </property>"
        + "</bean>"
    );

    assertThat(context.getBean("currentTraceContext", CurrentTraceContext.class))
        .extracting("scopeDecorators")
        .allSatisfy(l -> assertThat(((List) l).get(0)).isInstanceOf(StrictScopeDecorator.class));
  }
}
