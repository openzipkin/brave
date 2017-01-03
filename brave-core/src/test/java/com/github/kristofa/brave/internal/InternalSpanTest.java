package com.github.kristofa.brave.internal;

import com.github.kristofa.brave.SpanId;
import com.twitter.zipkin.gen.Span;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import static org.assertj.core.api.Assertions.assertThat;

public class InternalSpanTest {
  static {
    InternalSpan.initializeInstanceForTests();
  }

  SpanId spanId = SpanId.builder().traceIdHigh(1).traceId(2).parentId(3L).spanId(4L).build();

  @Test
  public void context_nullWhenSpanIsEmpty() {
    // pretend someone created an invalid span explicitly or by some accident of codec
    Span span = new Span();
    assertThat(InternalSpan.instance.context(span))
        .isNull();
  }

  @Test
  public void context_returnsSameObjectWhenSet() {
    Span span = InternalSpan.instance.toSpan(spanId);

    assertThat(InternalSpan.instance.context(span))
        .isSameAs(spanId);
  }

  @Test
  public void context_backFillsSpan() {
    Span span = InternalSpan.instance.toSpan(spanId);
    // If someone created a span externally, the context field would be unset
    // This is deprecated practice, so we shouldn't break.
    Whitebox.setInternalState(span, "context", (Object) null);

    SpanId backfilled = InternalSpan.instance.context(span);
    assertThat(backfilled).isEqualTo(spanId);

    // after the state is set, future calls will return the same reference
    assertThat(InternalSpan.instance.context(span))
        .isSameAs(backfilled);
  }
}
