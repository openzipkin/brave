package brave.spring.beans;

import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;
import zipkin.reporter.AsyncReporter;
import zipkin.reporter.Encoding;
import zipkin.reporter.ReporterMetrics;
import zipkin.reporter.Sender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AsyncReporterFactoryBeanTest {

  public static Sender SENDER = mock(Sender.class);
  public static ReporterMetrics METRICS = mock(ReporterMetrics.class);

  static {
    when(SENDER.encoding()).thenReturn(Encoding.JSON);
    when(SENDER.messageMaxBytes()).thenReturn(1024);
  }

  XmlBeans context;

  @After public void close() {
    if (context != null) context.close();
  }

  @Test public void sender() {
    context = new XmlBeans(""
        + "<bean id=\"asyncReporter\" class=\"brave.spring.beans.AsyncReporterFactoryBean\">\n"
        + "  <property name=\"sender\">\n"
        + "    <util:constant static-field=\"" + getClass().getName() + ".SENDER\"/>\n"
        + "  </property>\n"
        + "</bean>"
    );
    context.refresh();

    assertThat(context.getBean(AsyncReporter.class))
        .extracting("sender")
        .containsExactly(SENDER);
  }

  @Test public void metrics() {
    context = new XmlBeans(""
        + "<bean id=\"asyncReporter\" class=\"brave.spring.beans.AsyncReporterFactoryBean\">\n"
        + "  <property name=\"sender\">\n"
        + "    <util:constant static-field=\"" + getClass().getName() + ".SENDER\"/>\n"
        + "  </property>\n"
        + "  <property name=\"metrics\">\n"
        + "    <util:constant static-field=\"" + getClass().getName() + ".METRICS\"/>\n"
        + "  </property>\n"
        + "</bean>"
    );
    context.refresh();

    assertThat(context.getBean(AsyncReporter.class))
        .extracting("metrics")
        .containsExactly(METRICS);
  }

  @Test public void messageMaxBytes() {
    context = new XmlBeans(""
        + "<bean id=\"asyncReporter\" class=\"brave.spring.beans.AsyncReporterFactoryBean\">\n"
        + "  <property name=\"sender\">\n"
        + "    <util:constant static-field=\"" + getClass().getName() + ".SENDER\"/>\n"
        + "  </property>\n"
        + "  <property name=\"messageMaxBytes\" value=\"512\"/>\n"
        + "</bean>"
    );
    context.refresh();

    assertThat(context.getBean(AsyncReporter.class))
        .extracting("messageMaxBytes")
        .containsExactly(512);
  }

  @Test public void messageTimeout() {
    context = new XmlBeans(""
        + "<bean id=\"asyncReporter\" class=\"brave.spring.beans.AsyncReporterFactoryBean\">\n"
        + "  <property name=\"sender\">\n"
        + "    <util:constant static-field=\"" + getClass().getName() + ".SENDER\"/>\n"
        + "  </property>\n"
        + "  <property name=\"messageTimeout\" value=\"500\"/>\n"
        + "</bean>"
    );
    context.refresh();

    assertThat(context.getBean(AsyncReporter.class))
        .extracting("messageTimeoutNanos")
        .containsExactly(TimeUnit.MILLISECONDS.toNanos(500));
  }

  @Test public void closeTimeout() {
    context = new XmlBeans(""
        + "<bean id=\"asyncReporter\" class=\"brave.spring.beans.AsyncReporterFactoryBean\">\n"
        + "  <property name=\"sender\">\n"
        + "    <util:constant static-field=\"" + getClass().getName() + ".SENDER\"/>\n"
        + "  </property>\n"
        + "  <property name=\"closeTimeout\" value=\"500\"/>\n"
        + "</bean>"
    );
    context.refresh();

    assertThat(context.getBean(AsyncReporter.class))
        .extracting("closeTimeoutNanos")
        .containsExactly(TimeUnit.MILLISECONDS.toNanos(500));
  }

  @Test public void queuedMaxSpans() {
    context = new XmlBeans(""
        + "<bean id=\"asyncReporter\" class=\"brave.spring.beans.AsyncReporterFactoryBean\">\n"
        + "  <property name=\"sender\">\n"
        + "    <util:constant static-field=\"" + getClass().getName() + ".SENDER\"/>\n"
        + "  </property>\n"
        + "  <property name=\"queuedMaxSpans\" value=\"10\"/>\n"
        + "</bean>"
    );
    context.refresh();

    assertThat(context.getBean(AsyncReporter.class))
        .extracting("pending.maxSize")
        .containsExactly(10);
  }

  @Test public void queuedMaxBytes() {
    context = new XmlBeans(""
        + "<bean id=\"asyncReporter\" class=\"brave.spring.beans.AsyncReporterFactoryBean\">\n"
        + "  <property name=\"sender\">\n"
        + "    <util:constant static-field=\"" + getClass().getName() + ".SENDER\"/>\n"
        + "  </property>\n"
        + "  <property name=\"queuedMaxBytes\" value=\"512\"/>\n"
        + "</bean>"
    );
    context.refresh();

    assertThat(context.getBean(AsyncReporter.class))
        .extracting("pending.maxBytes")
        .containsExactly(512);
  }
}
