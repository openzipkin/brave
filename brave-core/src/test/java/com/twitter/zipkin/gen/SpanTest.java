package com.twitter.zipkin.gen;

import com.github.kristofa.brave.SpanId;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class SpanTest {

  @Test(expected = AssertionError.class)
  public void deprecatedConstructor_throwsAssertionError() {
    new Span();
  }

  @Test(expected = AssertionError.class)
  public void setTrace_id_high_throwsAssertionError() {
    Span.create(SpanId.builder().spanId(1L).build()).setTrace_id_high(1L);
  }

  @Test(expected = AssertionError.class)
  public void setTrace_id_throwsAssertionError() {
    Span.create(SpanId.builder().spanId(1L).build()).setTrace_id(1L);
  }

  @Test(expected = AssertionError.class)
  public void setParent_id_throwsAssertionError() {
    Span.create(SpanId.builder().spanId(1L).build()).setParent_id(1L);
  }

  @Test(expected = AssertionError.class)
  public void setId_throwsAssertionError() {
    Span.create(SpanId.builder().spanId(1L).build()).setId(1L);
  }

  @Test(expected = AssertionError.class)
  public void setDebug_throwsAssertionError() {
    Span.create(SpanId.builder().spanId(1L).build()).setDebug(false);
  }

  @Test
  public void testNameLowercase() {
    assertEquals("spanname", Span.create(SpanId.builder().spanId(1L).build())
        .setName("SpanName").getName());
  }

  @Test
  public void setName_coercesNullToEmptyString() {
    Span span = Span.create(SpanId.builder().spanId(1L).build()).setName("foo");

    assertThat(span.setName(null).getName()).isEqualTo("");
  }

  /** Use addToAnnotations as opposed to attempting to mutate the collection */
  @Test(expected = UnsupportedOperationException.class)
  public void getAnnotations_returnsUnmodifiable() {
    Span.create(SpanId.builder().spanId(1L).build()).getAnnotations().add(null);
  }

  /** Use addToBinary_annotations as opposed to attempting to mutate the collection */
  @Test(expected = UnsupportedOperationException.class)
  public void getBinary_annotations_returnsUnmodifiable() {
    Span.create(SpanId.builder().spanId(1L).build()).getBinary_annotations().add(null);
  }

  @Test
  public void toStringIsJson() {
    long traceId = -692101025335252320L;
    Span span = Span.create(SpanId.builder().spanId(traceId).build())
        .setName("get")
        .setTimestamp(1444438900939000L)
        .setDuration(376000L);

    assertEquals("{\"traceId\":\"f66529c8cc356aa0\",\"id\":\"f66529c8cc356aa0\",\"name\":\"get\",\"timestamp\":1444438900939000,\"duration\":376000}", span.toString());
  }

  @Test
  public void toSpan_128() {
    SpanId id = SpanId.builder().traceIdHigh(1).traceId(2).spanId(3).parentId(2L).build();

    Span span = Span.create(id);
    assertThat(span.getTrace_id_high()).isEqualTo(id.traceIdHigh);
    assertThat(span.getTrace_id()).isEqualTo(id.traceId);
    assertThat(span.getId()).isEqualTo(id.spanId);
    assertThat(span.getParent_id()).isEqualTo(id.parentId);
  }
}
