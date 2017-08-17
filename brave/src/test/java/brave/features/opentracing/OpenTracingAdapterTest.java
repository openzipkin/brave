package brave.features.opentracing;

import brave.Tracing;
import brave.propagation.TraceContext;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import zipkin.Constants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.data.MapEntry.entry;
import static zipkin.internal.Util.UTF_8;

/**
 * This shows how one might make an OpenTracing adapter for Brave, and how to navigate in and out
 * of the core concepts.
 */
public class OpenTracingAdapterTest {
  List<zipkin.Span> spans = new ArrayList<>();
  Tracing brave = Tracing.newBuilder().reporter(spans::add).build();
  BraveTracer opentracing = BraveTracer.wrap(brave);

  @After public void close() {
    Tracing.current().close();
  }

  @Test public void startWithOpenTracingAndFinishWithBrave() {
    io.opentracing.Span openTracingSpan = opentracing.buildSpan("encode")
        .withTag(Constants.LOCAL_COMPONENT, "codec")
        .withStartTimestamp(1L).start();

    brave.Span braveSpan = ((BraveSpan) openTracingSpan).unwrap();

    braveSpan.annotate(2L, "pump fake");
    braveSpan.finish(3L);

    checkSpanReportedToZipkin();
  }

  @Test public void startWithBraveAndFinishWithOpenTracing() {
    brave.Span braveSpan = brave.tracer().newTrace().name("encode")
        .tag(Constants.LOCAL_COMPONENT, "codec")
        .start(1L);

    io.opentracing.Span openTracingSpan = BraveSpan.wrap(braveSpan);

    openTracingSpan.log(2L, "pump fake");
    openTracingSpan.finish(3L);

    checkSpanReportedToZipkin();
  }

  @Test
  public void extractTraceContext() throws Exception {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("X-B3-TraceId", "0000000000000001");
    map.put("X-B3-SpanId", "0000000000000002");
    map.put("X-B3-Sampled", "1");

    BraveSpanContext openTracingContext =
        opentracing.extract(Format.Builtin.HTTP_HEADERS, new TextMapExtractAdapter(map));

    assertThat(openTracingContext.unwrap())
        .isEqualTo(TraceContext.newBuilder()
            .traceId(1L)
            .spanId(2L)
            .shared(true)
            .sampled(true).build());
  }

  @Test
  public void injectTraceContext() throws Exception {
    TraceContext context = TraceContext.newBuilder()
        .traceId(1L)
        .spanId(2L)
        .sampled(true).build();

    Map<String, String> map = new LinkedHashMap<>();
    TextMapInjectAdapter carrier = new TextMapInjectAdapter(map);
    opentracing.inject(BraveSpanContext.wrap(context), Format.Builtin.HTTP_HEADERS, carrier);

    assertThat(map).containsExactly(
        entry("X-B3-TraceId", "0000000000000001"),
        entry("X-B3-SpanId", "0000000000000002"),
        entry("X-B3-Sampled", "1")
    );
  }

  void checkSpanReportedToZipkin() {
    assertThat(spans).first().satisfies(s -> {
          assertThat(s.name).isEqualTo("encode");
          assertThat(s.timestamp).isEqualTo(1L);
          assertThat(s.annotations).extracting(a -> a.timestamp, a -> a.value)
              .containsExactly(tuple(2L, "pump fake"));
          assertThat(s.binaryAnnotations).extracting(b -> b.key, b -> new String(b.value, UTF_8))
              .containsExactly(tuple(Constants.LOCAL_COMPONENT, "codec"));
          assertThat(s.duration).isEqualTo(2L);
        }
    );
  }
}
