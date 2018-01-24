package brave.interop; // intentionally in a different package

import brave.Tracer;
import brave.Tracing;
import brave.propagation.TraceContext;
import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.ThreadLocalServerClientAndLocalSpanState;
import com.github.kristofa.brave.TracerAdapter;
import com.github.kristofa.brave.internal.InternalSpan;
import com.twitter.zipkin.gen.Span;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zipkin.Constants;
import zipkin2.Annotation;
import zipkin2.reporter.Reporter;

import static com.github.kristofa.brave.TracerAdapter.getServerSpan;
import static com.github.kristofa.brave.TracerAdapter.setServerSpan;
import static com.github.kristofa.brave.TracerAdapter.toSpan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class TracerAdapterTest {
  List<zipkin2.Span> spans = new ArrayList<>();
  AtomicLong epochMicros = new AtomicLong();
  Tracer brave4 = Tracing.newBuilder()
      .clock(epochMicros::incrementAndGet)
      // not lambda as this test is flakey and we need a concise toString
      .spanReporter(new Reporter<zipkin2.Span>() {
        @Override public void report(zipkin2.Span span) {
          spans.add(span);
        }

        @Override public String toString() {
          return "AddToList{" + spans + "}";
        }
      })
      .build()
      .tracer();
  Brave brave3 = TracerAdapter.newBrave(brave4);

  @Before public void clearBrave3State() {
    ThreadLocalServerClientAndLocalSpanState.clear();
  }

  @After public void close() {
    Tracing.current().close();
  }

  @Test public void startWithLocalTracerAndFinishWithTracer() {
    SpanId spanId = brave3.localTracer().startNewSpan("codec", "encode", 1L);

    brave.Span span = toSpan(brave4, spanId);

    ensureEquivalent(span.context(), InternalSpan.instance.context(
        brave3.localSpanThreadBinder().getCurrentLocalSpan()
    ));

    span.annotate(2L, "pump fake");
    span.finish(3L);

    zipkin2.Span s = checkLocalSpanReportedToZipkin(
        span.context(), brave3.localSpanThreadBinder().getCurrentLocalSpan()
    );
    assertThat(s.duration()).isEqualTo(2L);
  }

  @Test public void startWithCurrentLocalSpanAndFinishWithTracer() {
    brave3.localTracer().startNewSpan("codec", "encode", 1L);

    Span brave3Span = brave3.localSpanThreadBinder().getCurrentLocalSpan();

    brave.Span span = toSpan(brave4, brave3Span);

    ensureEquivalent(span.context(), InternalSpan.instance.context(brave3Span));

    span.annotate(2L, "pump fake");
    span.finish(3L);

    zipkin2.Span s = checkLocalSpanReportedToZipkin(
        span.context(), brave3.localSpanThreadBinder().getCurrentLocalSpan()
    );
    assertThat(s.duration()).isEqualTo(2L);
  }

  @Test public void startWithTracerAndFinishWithLocalTracer_doesntSupportDuration() {
    brave.Span brave4Span = brave4.newTrace().name("encode")
        .tag(Constants.LOCAL_COMPONENT, "codec")
        .start(1L);

    com.twitter.zipkin.gen.Span brave3Span = toSpan(brave4Span.context());

    ensureEquivalent(brave4Span.context(), InternalSpan.instance.context(brave3Span));

    brave3.localSpanThreadBinder().setCurrentSpan(brave3Span);

    brave3.localTracer().submitAnnotation("pump fake", 2L);
    brave3.localTracer().finishSpan(2L /* duration */);

    zipkin2.Span s = checkLocalSpanReportedToZipkin(
        brave4Span.context(), brave3.localSpanThreadBinder().getCurrentLocalSpan()
    );

    // starting a local span with one api and finishing with another is arbitrary.
    // adding infrastructure to readback timestamps isn't worth it, as it exposes
    // internal apis
    assertThat(s.duration()).isNull();
  }

  @Test public void startWithClientTracerAndFinishWithTracer() {
    SpanId spanId = brave3.clientTracer().startNewSpan("get");
    brave3.clientTracer().setClientSent();

    brave.Span span = toSpan(brave4, spanId);

    ensureEquivalent(span.context(), InternalSpan.instance.context(
        brave3.clientSpanThreadBinder().getCurrentClientSpan()
    ));

    span.finish();

    checkClientSpanReportedToZipkin(
        span.context(), brave3.clientSpanThreadBinder().getCurrentClientSpan()
    );
  }

  @Test public void startWithCurrentClientSpanAndFinishWithTracer() {
    brave3.clientTracer().startNewSpan("get");
    brave3.clientTracer().setClientSent();

    Span brave3Span = brave3.clientSpanThreadBinder().getCurrentClientSpan();

    brave.Span span = toSpan(brave4, brave3Span);

    ensureEquivalent(span.context(), InternalSpan.instance.context(brave3Span));

    span.finish();

    checkClientSpanReportedToZipkin(
        span.context(), brave3.clientSpanThreadBinder().getCurrentClientSpan()
    );
  }

  @Test public void startWithTracerAndFinishWithClientTracer() {
    brave.Span brave4Span = brave4.newTrace().name("get")
        .kind(brave.Span.Kind.CLIENT)
        .start();

    com.twitter.zipkin.gen.Span brave3Span = toSpan(brave4Span.context());

    brave3.clientSpanThreadBinder().setCurrentSpan(brave3Span);

    ensureEquivalent(brave4Span.context(), InternalSpan.instance.context(brave3Span));

    brave3.clientTracer().setClientReceived();

    checkClientSpanReportedToZipkin(
        brave4Span.context(), brave3.clientSpanThreadBinder().getCurrentClientSpan()
    );
  }

  @Test public void startWithCurrentServerSpanAndFinishWithTracer() {
    brave3.serverTracer().setStateUnknown("get");
    brave3.serverTracer().setServerReceived();

    brave.Span span = getServerSpan(brave4, brave3.serverSpanThreadBinder());

    ensureEquivalent(span.context(), InternalSpan.instance.context(
        brave3.serverSpanThreadBinder().getCurrentServerSpan().getSpan()
    ));

    span.finish();

    checkServerSpanReportedToZipkin(
        span.context(), brave3.serverSpanThreadBinder().getCurrentServerSpan().getSpan()
    );
  }

  @Test public void startWithTracerAndFinishWithServerTracer() {
    brave.Span brave4Span = brave4.newTrace().name("get")
        .kind(brave.Span.Kind.SERVER)
        .start();

    setServerSpan(brave4Span.context(), brave3.serverSpanThreadBinder());

    ensureEquivalent(brave4Span.context(), InternalSpan.instance.context(
        brave3.serverSpanThreadBinder().getCurrentServerSpan().getSpan()
    ));

    brave3.serverTracer().setServerSend();

    checkServerSpanReportedToZipkin(
        brave4Span.context(), brave3.serverSpanThreadBinder().getCurrentServerSpan().getSpan()
    );
  }

  private void ensureEquivalent(TraceContext context, SpanId spanId) {
    assertThat(context.traceId()).isEqualTo(spanId.traceId);
    assertThat(context.parentId()).isEqualTo(spanId.nullableParentId());
    assertThat(context.spanId()).isEqualTo(spanId.spanId);
    assertThat(context.sampled()).isEqualTo(spanId.sampled()).isTrue();
  }

  zipkin2.Span checkLocalSpanReportedToZipkin(TraceContext context, Span span) {
    assertSpansReported(context, span);
    assertThat(spans).first().satisfies(s -> {
          assertThat(s.name()).isEqualTo("encode");
          assertThat(s.timestamp()).isEqualTo(1L);
          assertThat(s.annotations())
              .containsExactly(Annotation.create(2L, "pump fake"));
          assertThat(s.tags())
              .containsExactly(entry(Constants.LOCAL_COMPONENT, "codec"));
        }
    );
    return spans.get(0);
  }

  void checkClientSpanReportedToZipkin(TraceContext context, Span span) {
    assertSpansReported(context, span);
    assertThat(spans).first().satisfies(s -> {
          assertThat(s.name()).isEqualTo("get");
          assertThat(s.timestamp()).isGreaterThan(1L);
          assertThat(s.duration()).isNotZero();
          assertThat(s.kind()).isEqualTo(zipkin2.Span.Kind.CLIENT);
        }
    );
  }

  void checkServerSpanReportedToZipkin(TraceContext context, Span span) {
    assertSpansReported(context, span);
    assertThat(spans).first().satisfies(s -> {
          assertThat(s.name()).isEqualTo("get");
          assertThat(s.timestamp()).isGreaterThanOrEqualTo(1L);
          assertThat(s.duration()).isNotZero();
          assertThat(s.kind()).isEqualTo(zipkin2.Span.Kind.SERVER);
        }
    );
  }

  void assertSpansReported(TraceContext context, Span span) {
    assertThat(spans).withFailMessage(String.format(
        "Expected to close %s; brave3 current span %s; brave4 state %s",
        context, span, brave4
    )).isNotEmpty();
  }
}
