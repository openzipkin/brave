package brave.jaxrs2;

import brave.Tracing;
import brave.http.HttpTracing;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** This ensures all filters can be injected, supplied with only {@linkplain HttpTracing}. */
public class InjectionTest {

  Injector injector = Guice.createInjector(new AbstractModule() {
    @Override protected void configure() {
      bind(HttpTracing.class).toInstance(HttpTracing.create(Tracing.newBuilder().build()));
    }
  });

  @After public void close(){
    Tracing.current().close();
  }

  @Test public void tracingClientFilter() throws Exception {
    assertThat(injector.getInstance(TracingClientFilter.class))
        .isNotNull();
  }

  @Test public void tracingContainerFilter() throws Exception {
    assertThat(injector.getInstance(TracingContainerFilter.class))
        .isNotNull();
  }

  @Test public void tracingFeature() throws Exception {
    assertThat(injector.getInstance(TracingFeature.class))
        .isNotNull();
  }
}
