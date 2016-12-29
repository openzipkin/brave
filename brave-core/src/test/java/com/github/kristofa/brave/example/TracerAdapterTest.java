package com.github.kristofa.brave.example; // intentionally in a different package

import brave.Tracer;
import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.TracerAdapter;
import com.twitter.zipkin.gen.Span;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import zipkin.Constants;

import static com.github.kristofa.brave.TracerAdapter.toSpan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static zipkin.internal.Util.UTF_8;

public class TracerAdapterTest {

  List<zipkin.Span> spans = new ArrayList<>();
  Tracer brave4 = Tracer.newBuilder().reporter(spans::add).build();
  Brave brave3 = TracerAdapter.newBrave(brave4);

  @Test public void startWithLocalTracerAndFinishWithTracer() {
    SpanId spanId = brave3.localTracer().startNewSpan("codec", "encode", 1L);

    brave.Span span = toSpan(brave4, spanId);

    span.annotate(2L, "pump fake");
    span.finish(3L);

    checkSpanReportedToZipkin();
  }

  @Test public void startWithCurrentSpanAndFinishWithTracer() {
    brave3.localTracer().startNewSpan("codec", "encode", 1L);

    Span brave3Span = brave3.localSpanThreadBinder().getCurrentLocalSpan();

    brave.Span span = toSpan(brave4, brave3Span);

    span.annotate(2L, "pump fake");
    span.finish(3L);

    checkSpanReportedToZipkin();
  }

  @Test public void startWithTracerAndFinishWithLocalTracer() {
    brave.Span brave4Span = brave4.newTrace().name("encode")
        .tag(Constants.LOCAL_COMPONENT, "codec")
        .start(1L);

    com.twitter.zipkin.gen.Span brave3Span = toSpan(brave4Span.context());
    brave3.localSpanThreadBinder().setCurrentSpan(brave3Span);

    brave3.localTracer().submitAnnotation("pump fake", 2L);
    brave3.localTracer().finishSpan(2L /* duration */);

    checkSpanReportedToZipkin();
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
