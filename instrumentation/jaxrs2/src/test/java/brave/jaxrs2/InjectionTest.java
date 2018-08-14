package brave.jaxrs2;

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

/** This ensures all filters can be injected, supplied with only {@linkplain HttpTracing}. */
public class InjectionTest {
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

  @Test public void tracingClientFilter() {
    assertThat(injector.getInstance(TracingClientFilter.class))
        .isNotNull();
  }

  @Test public void spanCustomizingContainerFilter() {
    SpanCustomizingContainerFilter filter =
        injector.getInstance(SpanCustomizingContainerFilter.class);

    assertThat(filter.parser.getClass())
        .isSameAs(ContainerParser.class);
  }

  @Test public void spanCustomizingContainerFilter_resource() {
    SpanCustomizingContainerFilter filter = injector.createChildInjector(new AbstractModule() {
      @Override protected void configure() {
        bind(ContainerParser.class).toInstance(ContainerParser.NOOP);
      }
    }).getInstance(SpanCustomizingContainerFilter.class);

    assertThat(filter.parser)
        .isSameAs(ContainerParser.NOOP);
  }
}
