/*
 * Copyright 2013-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.baggage;

import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.propagation.TraceIdContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** This only tests things not already covered in {@link BaggageFieldTest} */
@RunWith(MockitoJUnitRunner.class)
public class BaggageFieldsTest {
  TraceContext onlyMandatoryFields = TraceContext.newBuilder().traceId(1).spanId(2).build();
  TraceContext context = TraceContext.newBuilder()
    .traceIdHigh(1L)
    .traceId(2L)
    .parentId(3L)
    .spanId(4L)
    .sampled(true)
    .build();
  TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.create(context);
  TraceContextOrSamplingFlags extractedTraceId = TraceContextOrSamplingFlags.newBuilder()
    .traceIdContext(TraceIdContext.newBuilder().traceIdHigh(1L).traceId(2L).sampled(true).build())
    .build();

  @Test public void traceId() {
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

  @Test public void parentId() {
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

  @Test public void spanId() {
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

  @Test public void sampled() {
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

  @Test public void constant() {
    BaggageField constant = BaggageFields.constant("foo", "bar");
    assertThat(constant.getValue(context)).isEqualTo("bar");
    assertThat(constant.getValue(extracted)).isEqualTo("bar");

    BaggageField constantNull = BaggageFields.constant("foo", null);
    assertThat(constantNull.getValue(context)).isNull();
    assertThat(constantNull.getValue(extracted)).isNull();
  }
}
