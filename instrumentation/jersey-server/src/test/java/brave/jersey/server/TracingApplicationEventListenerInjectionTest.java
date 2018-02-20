package brave.jersey.server;

import brave.Tracing;
import brave.http.HttpTracing;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TracingApplicationEventListenerInjectionTest {

  Injector injector = Guice.createInjector(new AbstractModule() {
    @Override protected void configure() {
      bind(HttpTracing.class).toInstance(HttpTracing.create(Tracing.newBuilder().build()));
    }
  });

  @After public void close() {
    Tracing.current().close();
  }

  @Test public void onlyRequiresHttpTracing() {
    assertThat(injector.getInstance(TracingApplicationEventListener.class))
        .isNotNull();
  }
}
