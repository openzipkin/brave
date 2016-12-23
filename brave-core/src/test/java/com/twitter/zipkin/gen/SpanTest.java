package com.twitter.zipkin.gen;

import com.github.kristofa.brave.SpanId;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class SpanTest {
  @Test
  public void testNameLowercase() {
    assertEquals("spanname", new Span().setName("SpanName").getName());
  }

  @Test
  public void toStringIsJson() {
    long traceId = -692101025335252320L;
    Span span = new Span()
        .setTrace_id(traceId)
        .setName("get")
        .setId(traceId)
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
