package brave.jersey.server;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.ThreadLocalCurrentTraceContext;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.After;
import org.junit.Test;
import zipkin2.reporter.Reporter;

import static org.assertj.core.api.Assertions.assertThat;

public class TracingApplicationEventListenerInjectionTest {
  Tracing tracing = Tracing.newBuilder()
      .currentTraceContext(ThreadLocalCurrentTraceContext.create())
      .spanReporter(Reporter.NOOP)
      .build();

  Injector injector = Guice.createInjector(new AbstractModule() {
    @Override protected void configure() {
      bind(HttpTracing.class).toInstance(HttpTracing.create(tracing));
    }
  });

  @After public void close() {
    tracing.close();
  }

  @Test public void onlyRequiresHttpTracing() {
    assertThat(injector.getInstance(TracingApplicationEventListener.class))
        .isNotNull();
  }
}
