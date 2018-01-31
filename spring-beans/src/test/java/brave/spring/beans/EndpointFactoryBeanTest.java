package brave.spring.beans;

import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.BeanCreationException;
import zipkin2.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public class EndpointFactoryBeanTest {
  XmlBeans context;

  @After public void close() {
    if (context != null) context.close();
  }

  @Test public void serviceName() {
    context = new XmlBeans(""
        + "<bean id=\"endpoint\" class=\"brave.spring.beans.EndpointFactoryBean\">\n"
        + "  <property name=\"serviceName\" value=\"brave-webmvc-example\"/>\n"
        + "</bean>"
    );

    assertThat(context.getBean("endpoint", Endpoint.class))
        .isEqualTo(Endpoint.newBuilder().serviceName("brave-webmvc-example").build());
  }

  @Test public void ip() {
    context = new XmlBeans(""
        + "<bean id=\"endpoint\" class=\"brave.spring.beans.EndpointFactoryBean\">\n"
        + "  <property name=\"serviceName\" value=\"brave-webmvc-example\"/>\n"
        + "  <property name=\"ip\" value=\"1.2.3.4\"/>\n"
        + "</bean>"
    );

    assertThat(context.getBean("endpoint", Endpoint.class))
        .isEqualTo(Endpoint.newBuilder()
            .serviceName("brave-webmvc-example")
            .ip("1.2.3.4")
            .build());
  }

  @Test public void ip_malformed() {
    context = new XmlBeans(""
        + "<bean id=\"endpoint\" class=\"brave.spring.beans.EndpointFactoryBean\">\n"
        + "  <property name=\"serviceName\" value=\"brave-webmvc-example\"/>\n"
        + "  <property name=\"ip\" value=\"localhost\"/>\n"
        + "</bean>"
    );

    try {
      context.getBean("endpoint", Endpoint.class);
      failBecauseExceptionWasNotThrown(BeanCreationException.class);
    } catch (BeanCreationException e) {
      assertThat(e)
          .hasMessageContaining("endpoint.ip: localhost is not an IP literal");
    }
  }

  @Test public void port() {
    context = new XmlBeans(""
        + "<bean id=\"endpoint\" class=\"brave.spring.beans.EndpointFactoryBean\">\n"
        + "  <property name=\"serviceName\" value=\"brave-webmvc-example\"/>\n"
        + "  <property name=\"ip\" value=\"1.2.3.4\"/>\n"
        + "  <property name=\"port\" value=\"8080\"/>\n"
        + "</bean>"
    );

    assertThat(context.getBean("endpoint", Endpoint.class))
        .isEqualTo(Endpoint.newBuilder()
            .serviceName("brave-webmvc-example")
            .ip("1.2.3.4")
            .port(8080).build());
  }
}
