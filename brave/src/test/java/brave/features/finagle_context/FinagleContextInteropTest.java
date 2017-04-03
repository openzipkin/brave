package brave.features.finagle_context;

import brave.Span;
import brave.Tracer;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import com.twitter.finagle.context.MarshalledContext;
import com.twitter.finagle.tracing.Flags$;
import com.twitter.finagle.tracing.SpanId;
import com.twitter.finagle.tracing.Trace;
import com.twitter.finagle.tracing.Trace$;
import com.twitter.finagle.tracing.TraceId;
import com.twitter.io.Buf;
import com.twitter.util.Local;
import java.lang.reflect.Field;
import org.junit.Test;
import scala.Option;
import scala.Some;
import scala.collection.immutable.Map;
import scala.runtime.AbstractFunction0;

import static com.twitter.finagle.context.Contexts.broadcast;
import static org.assertj.core.api.Assertions.assertThat;

public class FinagleContextInteropTest {

  @Test public void finagleBraveInterop() throws Exception {
    Tracer tracer = Tracer.newBuilder().currentTraceContext(new FinagleCurrentTraceContext()).build();

    Span parent = tracer.newTrace(); // start a trace in Brave
    try (Tracer.SpanInScope wsParent = tracer.withSpanInScope(parent)) {
      // Inside the parent scope, trace context is consistent between finagle and brave
      assertThat(tracer.currentSpan().context().spanId())
          .isEqualTo(Trace.id().spanId().self());

      // create a child in finagle
      Trace.letId(Trace.nextId(), false, new AbstractFunction0<Void>() {
        @Override public Void apply() {
          // Inside the child scope, trace context is consistent between finagle and brave
          assertThat(tracer.currentSpan().context().spanId())
              .isEqualTo(Trace.id().spanId().self());

          // The child span has the correct parent (consistent between finagle and brave)
          assertThat(tracer.currentSpan().context().parentId())
              .isEqualTo(parent.context().spanId())
              .isEqualTo(Trace.id().parentId().self());

          return null;
        }
      });

      // After leaving the child scope, trace context is consistent between finagle and brave
      assertThat(tracer.currentSpan().context().spanId())
          .isEqualTo(Trace.id().spanId().self());

      // The parent span was reverted
      assertThat(tracer.currentSpan().context().spanId())
          .isEqualTo(parent.context().spanId())
          .isEqualTo(Trace.id().spanId().self());
    }

    // Outside a scope, trace context is consistent between finagle and brave
    assertThat(tracer.currentSpan()).isNull();
    assertThat(Trace.idOption().isDefined()).isFalse();
  }

  /**
   * In Finagle, let forms are used to apply an trace context to a function. This is implemented
   * under the scenes by a try-finally block using {@link Local#set(Option)}. The below code uses
   * this detail to allow interop between finagle and {@link CurrentTraceContext}.
   */
  static class FinagleCurrentTraceContext extends CurrentTraceContext {
    static final MarshalledContext.Key<TraceId> TRACE_ID_KEY = Trace$.MODULE$.idCtx();

    final Local broadcastLocal;

    FinagleCurrentTraceContext() throws NoSuchFieldException, IllegalAccessException {
      Field field = MarshalledContext.class.getDeclaredField("local");
      field.setAccessible(true);
      this.broadcastLocal = (Local) field.get(broadcast());
    }

    @Override public TraceContext get() {
      Option<TraceId> option = broadcast().get(Trace$.MODULE$.idCtx());
      if (option.isEmpty()) return null;
      return toTraceContext(option.get());
    }

    @Override public Scope newScope(TraceContext currentSpan) {
      Map<Buf, MarshalledContext.Cell> saved = broadcast().env();
      Map<Buf, MarshalledContext.Cell> update = broadcast().env().updated(
          TRACE_ID_KEY.marshalId(),
          broadcast().Real().apply(TRACE_ID_KEY, new Some(toTraceId(currentSpan)))
      );
      broadcastLocal.set(new Some(update));
      return () -> broadcastLocal.set(new Some(saved));
    }
  }

  static TraceContext toTraceContext(TraceId id) {
    return TraceContext.newBuilder()
        .traceId(id.traceId().self())
        .parentId(id._parentId().isEmpty() ? null : id.parentId().self())
        .spanId(id.spanId().self())
        .sampled(id.getSampled().isEmpty() ? null : id.getSampled().get())
        .debug(id.flags().isDebug())
        // .shared(isn't known in finagle)
        .build();
  }

  static TraceId toTraceId(TraceContext context) {
    return new TraceId(
        Option.apply(SpanId.apply(context.traceId())),
        Option.apply(context.parentId() == null ? null : SpanId.apply(context.parentId())),
        SpanId.apply(context.spanId()),
        Option.apply(context.sampled()),
        Flags$.MODULE$.apply(context.debug() ? 1 : 0)
    );
  }
}
