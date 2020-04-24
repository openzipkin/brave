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

import brave.Tracing;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.internal.baggage.BaggageContext;
import brave.internal.baggage.ExtraBaggageContext;
import brave.internal.baggage.ExtraBaggageFields;
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

public class BaggageFieldTest {
  static final BaggageField REQUEST_ID = BaggageField.create("requestId");
  static final BaggageField AMZN_TRACE_ID = BaggageField.create("x-amzn-trace-id");

  Propagation.Factory factory = BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
    .add(SingleBaggageField.newBuilder(REQUEST_ID).addKeyName("x-vcap-request-id").build())
    .add(SingleBaggageField.remote(AMZN_TRACE_ID)).build();
  Propagation<String> propagation = factory.get();
  Extractor<Map<String, String>> extractor = propagation.extractor(Map::get);

  TraceContextOrSamplingFlags emptyExtraction = extractor.extract(Collections.emptyMap());
  String requestId = "abcdef";
  TraceContextOrSamplingFlags requestIdExtraction =
    extractor.extract(Collections.singletonMap("x-vcap-request-id", requestId));

  TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).build();
  TraceContext emptyContext = factory.decorate(context);
  TraceContextOrSamplingFlags extraction = TraceContextOrSamplingFlags.create(emptyContext);
  TraceContext requestIdContext =
    context.toBuilder().extra(requestIdExtraction.extra()).build();

  @Test public void internalStorage() {
    assertThat(BaggageField.create("foo").context)
      .isSameAs(ExtraBaggageContext.get());

    BaggageContext context = mock(BaggageContext.class);
    assertThat(new BaggageField("context", context).context)
      .isSameAs(context);
  }

  @Test public void getAll_extracted() {
    assertThat(BaggageField.getAll(emptyExtraction))
      .containsExactly(REQUEST_ID, AMZN_TRACE_ID)
      .containsExactlyElementsOf(BaggageField.getAll(extraction));
  }

  @Test public void getAll() {
    assertThat(BaggageField.getAll(emptyContext))
      .containsExactly(REQUEST_ID, AMZN_TRACE_ID);

    try (Tracing tracing = Tracing.newBuilder().build();
         Scope ws = tracing.currentTraceContext().newScope(emptyContext)) {
      assertThat(BaggageField.getAll())
        .containsExactly(REQUEST_ID, AMZN_TRACE_ID);
    }
  }

  @Test public void getAll_doesntExist() {
    assertThat(BaggageField.getAll(TraceContextOrSamplingFlags.EMPTY)).isEmpty();
    assertThat(BaggageField.getAll(context)).isEmpty();
    assertThat(BaggageField.getAll()).isEmpty();

    try (Tracing tracing = Tracing.newBuilder().build();
         Scope ws = tracing.currentTraceContext().newScope(null)) {
      assertThat(BaggageField.getAll()).isEmpty();
    }
  }

  @Test public void getByName_doesntExist() {
    assertThat(BaggageField.getByName(emptyContext, "robots")).isNull();
    assertThat(BaggageField.getByName("robots")).isNull();

    try (Tracing tracing = Tracing.newBuilder().build();
         Scope ws = tracing.currentTraceContext().newScope(null)) {
      assertThat(BaggageField.getByName(REQUEST_ID.name())).isNull();
    }
  }

  @Test public void getByName() {
    assertThat(BaggageField.getByName(emptyContext, REQUEST_ID.name()))
      .isSameAs(REQUEST_ID);

    try (Tracing tracing = Tracing.newBuilder().build();
         Scope ws = tracing.currentTraceContext().newScope(emptyContext)) {
      assertThat(BaggageField.getByName(REQUEST_ID.name()))
        .isSameAs(REQUEST_ID);
    }
  }

  @Test public void getByName_extracted() {
    assertThat(BaggageField.getByName(emptyExtraction, REQUEST_ID.name()))
      .isSameAs(REQUEST_ID)
      .isSameAs(BaggageField.getByName(extraction, REQUEST_ID.name()));
  }

  @Test public void getByName_context_null() {
    // permits unguarded use of CurrentTraceContext.get()
    assertThat(BaggageField.getByName((TraceContext) null, "foo"))
      .isNull();
  }

  @Test public void getByName_invalid() {
    assertThatThrownBy(() -> BaggageField.getByName(context, ""))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> BaggageField.getByName(context, "    "))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test public void trimsName() {
    assertThat(BaggageField.create(" x-foo  ").name())
      .isEqualTo("x-foo");
  }

  @Test public void create_invalid() {
    assertThatThrownBy(() -> BaggageField.create(null))
      .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> BaggageField.create(""))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> BaggageField.create("    "))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test public void getValue_current_exists() {
    try (Tracing t = Tracing.newBuilder().propagationFactory(factory).build();
         Scope scope = t.currentTraceContext().newScope(requestIdContext)) {
      assertThat(REQUEST_ID.getValue())
        .isEqualTo(requestId);
    }
  }

  @Test public void getValue_current_doesntExist() {
    try (Tracing t = Tracing.newBuilder().propagationFactory(factory).build();
         Scope scope = t.currentTraceContext().newScope(requestIdContext)) {
      assertThat(AMZN_TRACE_ID.getValue())
        .isNull();
    }
  }

  @Test public void getValue_current_nothingCurrent() {
    assertThat(AMZN_TRACE_ID.getValue())
      .isNull();
  }

  @Test public void getValue_context_exists() {
    assertThat(REQUEST_ID.getValue(requestIdContext))
      .isEqualTo(requestId);
  }

  @Test public void getValue_context_doesntExist() {
    assertThat(AMZN_TRACE_ID.getValue(requestIdContext))
      .isNull();
    assertThat(AMZN_TRACE_ID.getValue(emptyContext))
      .isNull();
    assertThat(AMZN_TRACE_ID.getValue(context))
      .isNull();
  }

  @Test public void getValue_context_null() {
    // permits unguarded use of CurrentTraceContext.get()
    assertThat(REQUEST_ID.getValue((TraceContext) null))
      .isNull();
  }

  @Test public void getValue_extracted_exists() {
    assertThat(REQUEST_ID.getValue(requestIdExtraction))
      .isEqualTo(requestId);
  }

  @Test public void getValue_extracted_doesntExist() {
    assertThat(AMZN_TRACE_ID.getValue(requestIdExtraction))
      .isNull();
    assertThat(AMZN_TRACE_ID.getValue(emptyExtraction))
      .isNull();
    assertThat(AMZN_TRACE_ID.getValue(TraceContextOrSamplingFlags.EMPTY))
      .isNull();
  }

  @Test public void getValue_extracted_invalid() {
    assertThatThrownBy(() -> REQUEST_ID.getValue((TraceContextOrSamplingFlags) null))
      .isInstanceOf(NullPointerException.class);
  }

  @Test public void updateValue_current_exists() {
    try (Tracing t = Tracing.newBuilder().propagationFactory(factory).build();
         Scope scope = t.currentTraceContext().newScope(requestIdContext)) {
      REQUEST_ID.updateValue("12345");
      assertThat(REQUEST_ID.getValue())
        .isEqualTo("12345");
    }
  }

  @Test public void updateValue_current_doesntExist() {
    try (Tracing t = Tracing.newBuilder().propagationFactory(factory).build();
         Scope scope = t.currentTraceContext().newScope(requestIdContext)) {
      AMZN_TRACE_ID.updateValue("12345");
      assertThat(AMZN_TRACE_ID.getValue())
        .isEqualTo("12345");
    }
  }

  @Test public void updateValue_current_nothingCurrent() {
    AMZN_TRACE_ID.updateValue("12345");
    assertThat(AMZN_TRACE_ID.getValue())
      .isNull();
  }

  @Test public void updateValue_context_exists() {
    REQUEST_ID.updateValue(requestIdContext, "12345");
    assertThat(REQUEST_ID.getValue(requestIdContext))
      .isEqualTo("12345");
  }

  @Test public void updateValue_context_doesntExist() {
    AMZN_TRACE_ID.updateValue(requestIdContext, "12345");
    assertThat(AMZN_TRACE_ID.getValue(requestIdContext))
      .isEqualTo("12345");

    AMZN_TRACE_ID.updateValue(emptyContext, "12345");
    assertThat(AMZN_TRACE_ID.getValue(emptyContext))
      .isEqualTo("12345");

    AMZN_TRACE_ID.updateValue(context, "12345");
    assertThat(AMZN_TRACE_ID.getValue(context))
      .isNull();
  }

  @Test public void updateValue_context_null() {
    // permits unguarded use of CurrentTraceContext.get()
    REQUEST_ID.updateValue((TraceContext) null, null);
  }

  @Test public void updateValue_extracted_exists() {
    REQUEST_ID.updateValue(requestIdExtraction, "12345");
    assertThat(REQUEST_ID.getValue(requestIdExtraction))
      .isEqualTo("12345");
  }

  @Test public void updateValue_extracted_doesntExist() {
    AMZN_TRACE_ID.updateValue(requestIdExtraction, "12345");
    assertThat(AMZN_TRACE_ID.getValue(requestIdExtraction))
      .isEqualTo("12345");

    AMZN_TRACE_ID.updateValue(emptyExtraction, "12345");
    assertThat(AMZN_TRACE_ID.getValue(emptyExtraction))
      .isEqualTo("12345");

    AMZN_TRACE_ID.updateValue(TraceContextOrSamplingFlags.EMPTY, "12345");
  }

  @Test public void updateValue_extracted_invalid() {
    assertThatThrownBy(() -> REQUEST_ID.updateValue((TraceContextOrSamplingFlags) null, null))
      .isInstanceOf(NullPointerException.class);
  }

  @Test public void toString_onlyHasName() {
    assertThat(BaggageField.create("Foo"))
      .hasToString("BaggageField{Foo}"); // case preserved as that's the field name
  }

  /**
   * Ensures only lower-case name comparison is used in equals and hashCode. This allows {@link
   * BaggagePropagation} to deduplicate and {@link ExtraBaggageFields} to use these as keys.
   */
  @Test public void equalsAndHashCode() {
    // same field are equivalent
    BaggageField field = BaggageField.create("foo");
    assertThat(field).isEqualTo(field);
    assertThat(field).hasSameHashCodeAs(field);

    // different case format is equivalent
    BaggageField sameName = BaggageField.create("fOo");
    assertThat(field).isEqualTo(sameName);
    assertThat(field).hasSameHashCodeAs(sameName);

    // different values are not equivalent
    assertThat(field).isNotEqualTo(BaggageField.create("bar"));
    assertThat(field.hashCode()).isNotEqualTo(BaggageField.create("bar").hashCode());
  }
}
