package brave.http;

import brave.Tracing;
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.StrictCurrentTraceContext;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ITHttp {
  @Rule public ExpectedException thrown = ExpectedException.none();
  @Rule public MockWebServer server = new MockWebServer();

  protected ConcurrentLinkedDeque<Span> spans = new ConcurrentLinkedDeque<>();

  protected CurrentTraceContext currentTraceContext = new StrictCurrentTraceContext();
  protected HttpTracing httpTracing;

  @After public void close() throws Exception {
    Tracing current = Tracing.current();
    if (current != null) current.close();
  }

  Tracing.Builder tracingBuilder(Sampler sampler) {
    return Tracing.newBuilder()
        .spanReporter(s -> {
          // make sure the context was cleared prior to finish.. no leaks!
          TraceContext current = httpTracing.tracing().currentTraceContext().get();
          if (current != null) {
            assertThat(current.spanId())
                .isNotEqualTo(s.id());
          }
          spans.add(s);
        })
        .propagationFactory(ExtraFieldPropagation.newFactory(B3Propagation.FACTORY, "user-id"))
        .currentTraceContext(currentTraceContext)
        .sampler(sampler);
  }

  void assertReportedTagsInclude(String key, String... values) {
    assertThat(spans)
        .flatExtracting(s -> s.tags().entrySet())
        .filteredOn(e -> e.getKey().equals(key))
        .extracting(Map.Entry::getValue)
        .containsExactly(values);
  }
}
