package brave.http;

import brave.Tracing;
import brave.internal.StrictCurrentTraceContext;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import java.util.Collections;
import java.util.List;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.internal.Util;
import zipkin.storage.InMemoryStorage;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class ITHttp {
  @Rule public ExpectedException thrown = ExpectedException.none();
  @Rule public MockWebServer server = new MockWebServer();

  Endpoint local = Endpoint.builder().serviceName("local").ipv4(127 << 24 | 1).port(100).build();
  InMemoryStorage storage = new InMemoryStorage();

  protected CurrentTraceContext currentTraceContext = new StrictCurrentTraceContext();
  protected HttpTracing httpTracing;

  Tracing.Builder tracingBuilder(Sampler sampler) {
    return Tracing.newBuilder()
        .reporter(s -> {
          // make sure the context was cleared prior to finish.. no leaks!
          TraceContext current = httpTracing.tracing().currentTraceContext().get();
          if (current != null) {
            assertThat(current.spanId())
                .isNotEqualTo(s.id);
          }
          storage.spanConsumer().accept(asList(s));
        })
        .currentTraceContext(currentTraceContext)
        .localEndpoint(local)
        .sampler(sampler);
  }

  List<Span> collectedSpans() {
    List<List<Span>> result = storage.spanStore().getRawTraces();
    if (result.isEmpty()) return Collections.emptyList();
    assertThat(result).hasSize(1);
    return result.get(0);
  }

  void assertReportedTagsInclude(String key, String... values) {
    assertThat(collectedSpans())
        .flatExtracting(s -> s.binaryAnnotations)
        .filteredOn(b -> b.key.equals(key))
        .extracting(b -> new String(b.value, Util.UTF_8))
        .containsExactly(values);
  }
}
