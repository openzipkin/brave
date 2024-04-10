/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.baggage;

import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.propagation.TraceIdContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/** This only tests things not already covered in {@link BaggageFieldTest} */
@ExtendWith(MockitoExtension.class)
class BaggageFieldsTest {
  TraceContext onlyMandatoryFields = TraceContext.newBuilder().traceId(1).spanId(2).build();
  TraceContext context = TraceContext.newBuilder()
    .traceIdHigh(1L)
    .traceId(2L)
    .parentId(3L)
    .spanId(4L)
    .sampled(true)
    .build();
  TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.create(context);
  TraceContextOrSamplingFlags extractedTraceId = TraceContextOrSamplingFlags.create(
    TraceIdContext.newBuilder().traceIdHigh(1L).traceId(2L).sampled(true).build());

  @Test void traceId() {
    assertThat(BaggageFields.TRACE_ID.getValue(context))
      .isEqualTo(context.traceIdString());
    assertThat(BaggageFields.TRACE_ID.getValue(extracted))
      .isEqualTo(context.traceIdString());
    assertThat(BaggageFields.TRACE_ID.getValue(extractedTraceId))
      .isEqualTo(context.traceIdString());

    assertThat(BaggageFields.TRACE_ID.getValue(TraceContextOrSamplingFlags.EMPTY))
      .isNull();
    assertThat(BaggageFields.TRACE_ID.getValue((TraceContext) null))
      .isNull();
  }

  @Test void parentId() {
    assertThat(BaggageFields.PARENT_ID.getValue(context))
      .isEqualTo(context.parentIdString());
    assertThat(BaggageFields.PARENT_ID.getValue(extracted))
      .isEqualTo(context.parentIdString());

    assertThat(BaggageFields.PARENT_ID.getValue(onlyMandatoryFields))
      .isNull();
    assertThat(BaggageFields.PARENT_ID.getValue(TraceContextOrSamplingFlags.EMPTY))
      .isNull();
    assertThat(BaggageFields.PARENT_ID.getValue(extractedTraceId))
      .isNull();
    assertThat(BaggageFields.PARENT_ID.getValue((TraceContext) null))
      .isNull();
  }

  @Test void spanId() {
    assertThat(BaggageFields.SPAN_ID.getValue(context))
      .isEqualTo(context.spanIdString());
    assertThat(BaggageFields.SPAN_ID.getValue(extracted))
      .isEqualTo(context.spanIdString());

    assertThat(BaggageFields.SPAN_ID.getValue(TraceContextOrSamplingFlags.EMPTY))
      .isNull();
    assertThat(BaggageFields.SPAN_ID.getValue(extractedTraceId))
      .isNull();
    assertThat(BaggageFields.SPAN_ID.getValue((TraceContext) null))
      .isNull();
  }

  @Test void sampled() {
    assertThat(BaggageFields.SAMPLED.getValue(context))
      .isEqualTo("true");
    assertThat(BaggageFields.SAMPLED.getValue(extracted))
      .isEqualTo("true");
    assertThat(BaggageFields.SAMPLED.getValue(extractedTraceId))
      .isEqualTo("true");
    assertThat(BaggageFields.SAMPLED.getValue(TraceContextOrSamplingFlags.SAMPLED))
      .isEqualTo("true");
    assertThat(BaggageFields.SAMPLED.getValue(TraceContextOrSamplingFlags.NOT_SAMPLED))
      .isEqualTo("false");

    assertThat(BaggageFields.SAMPLED.getValue(onlyMandatoryFields))
      .isNull();
    assertThat(BaggageFields.SAMPLED.getValue(TraceContextOrSamplingFlags.EMPTY))
      .isNull();
    assertThat(BaggageFields.SAMPLED.getValue((TraceContext) null))
      .isNull();
  }

  @Test void constant() {
    BaggageField constant = BaggageFields.constant("foo", "bar");
    assertThat(constant.getValue(context)).isEqualTo("bar");
    assertThat(constant.getValue(extracted)).isEqualTo("bar");

    BaggageField constantNull = BaggageFields.constant("foo", null);
    assertThat(constantNull.getValue(context)).isNull();
    assertThat(constantNull.getValue(extracted)).isNull();
  }
}
