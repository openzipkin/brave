package brave.jersey.server;

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

  @After public void close() {
    Tracing.current().close();
  }

  @Test public void spanCustomizingApplicationEventListener() {
    SpanCustomizingApplicationEventListener filter =
        injector.getInstance(SpanCustomizingApplicationEventListener.class);

    assertThat(filter.parser.getClass())
        .isSameAs(EventParser.class);
  }

  @Test public void spanCustomizingApplicationEventListener_resource() {
    SpanCustomizingApplicationEventListener filter =
        injector.createChildInjector(new AbstractModule() {
          @Override protected void configure() {
            bind(EventParser.class).toInstance(EventParser.NOOP);
          }
        }).getInstance(SpanCustomizingApplicationEventListener.class);

    assertThat(filter.parser)
        .isSameAs(EventParser.NOOP);
  }

  @Test public void tracingApplicationEventListener() {
    TracingApplicationEventListener filter =
        injector.getInstance(TracingApplicationEventListener.class);

    assertThat(filter.parser.getClass())
        .isSameAs(EventParser.class);
  }

  @Test public void tracingApplicationEventListener_resource() {
    TracingApplicationEventListener filter = injector.createChildInjector(new AbstractModule() {
      @Override protected void configure() {
        bind(EventParser.class).toInstance(EventParser.NOOP);
      }
    }).getInstance(TracingApplicationEventListener.class);

    assertThat(filter.parser)
        .isSameAs(EventParser.NOOP);
  }
}
