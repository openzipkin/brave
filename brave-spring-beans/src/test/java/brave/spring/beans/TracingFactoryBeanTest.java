package brave.spring.beans;

import brave.Clock;
import brave.Tracing;
import brave.internal.StrictCurrentTraceContext;
import brave.sampler.Sampler;
import org.junit.After;
import org.junit.Test;
import zipkin.Endpoint;
import zipkin.reporter.Reporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class TracingFactoryBeanTest {
  public static Clock CLOCK = mock(Clock.class);

  XmlBeans context;

  @After public void close() {
    if (context != null) context.close();
  }

  @Test public void localServiceName() {
    context = new XmlBeans(""
        + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
        + "  <property name=\"localServiceName\" value=\"brave-webmvc-example\"/>\n"
        + "</bean>"
    );
    context.refresh();

    assertThat(context.getBean(Tracing.class))
        .extracting("tracer.localEndpoint")
        .extracting("serviceName")
        .containsExactly("brave-webmvc-example");
  }

  @Test public void localEndpoint() {
    context = new XmlBeans(""
        + "<bean id=\"localEndpoint\" class=\"brave.spring.beans.EndpointFactoryBean\">\n"
        + "  <property name=\"serviceName\" value=\"brave-webmvc-example\"/>\n"
        + "  <property name=\"ip\" value=\"1.2.3.4\"/>\n"
        + "  <property name=\"port\" value=\"8080\"/>\n"
        + "</bean>"
        , ""
        + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
        + "  <property name=\"localEndpoint\" ref=\"localEndpoint\"/>\n"
        + "</bean>"
    );
    context.refresh();

    assertThat(context.getBean(Tracing.class))
        .extracting("tracer.localEndpoint")
        .containsExactly(Endpoint.builder()
            .serviceName("brave-webmvc-example")
            .ipv4(1 << 24 | 2 << 16 | 3 << 8 | 4)
            .port(8080).build());
  }

  @Test public void reporter() {
    context = new XmlBeans(""
        + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
        + "  <property name=\"reporter\">\n"
        + "    <util:constant static-field=\"zipkin.reporter.Reporter.CONSOLE\"/>\n"
        + "  </property>\n"
        + "</bean>"
    );
    context.refresh();

    assertThat(context.getBean(Tracing.class))
        .extracting("tracer.recorder.reporter")
        .containsExactly(Reporter.CONSOLE);
  }

  @Test public void clock() {
    context = new XmlBeans(""
        + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
        + "  <property name=\"clock\">\n"
        + "    <util:constant static-field=\"" + getClass().getName() + ".CLOCK\"/>\n"
        + "  </property>\n"
        + "</bean>"
    );
    context.refresh();

    assertThat(context.getBean(Tracing.class))
        .extracting("tracer.clock")
        .containsExactly(CLOCK);
  }

  @Test public void sampler() {
    context = new XmlBeans(""
        + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
        + "  <property name=\"sampler\">\n"
        + "    <util:constant static-field=\"brave.sampler.Sampler.NEVER_SAMPLE\"/>\n"
        + "  </property>\n"
        + "</bean>"
    );
    context.refresh();

    assertThat(context.getBean(Tracing.class))
        .extracting("tracer.sampler")
        .containsExactly(Sampler.NEVER_SAMPLE);
  }

  @Test public void currentTraceContext() {
    context = new XmlBeans(""
        + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
        + "  <property name=\"currentTraceContext\">\n"
        + "    <bean class=\"brave.internal.StrictCurrentTraceContext\"/>\n"
        + "  </property>\n"
        + "</bean>"
    );
    context.refresh();

    assertThat(context.getBean(Tracing.class))
        .extracting("tracer.currentTraceContext")
        .allMatch(o -> o instanceof StrictCurrentTraceContext);
  }

  @Test public void traceId128Bit() {
    context = new XmlBeans(""
        + "<bean id=\"tracing\" class=\"brave.spring.beans.TracingFactoryBean\">\n"
        + "  <property name=\"traceId128Bit\" value=\"true\"/>\n"
        + "</bean>"
    );
    context.refresh();

    assertThat(context.getBean(Tracing.class))
        .extracting("tracer.traceId128Bit")
        .containsExactly(true);
  }
}
