/*
 * Copyright 2013-2024 The OpenZipkin Authors
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
package brave;

import brave.baggage.BaggageFields;
import brave.propagation.TraceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/** This only tests things not already covered in {@link TagTest} */
@ExtendWith(MockitoExtension.class)
class TagsTest {
  @Mock SpanCustomizer span;

  @Test void error() {
    Tags.ERROR.tag(new RuntimeException("this cake is a lie"), span);

    verify(span).tag("error", "this cake is a lie");
  }

  @Test void error_noMessage() {
    Tags.ERROR.tag(new RuntimeException(), span);

    verify(span).tag("error", "RuntimeException");
  }

  @Test void error_anonymous() {
    Tags.ERROR.tag(new RuntimeException() {}, span);

    verify(span).tag("error", "RuntimeException");
  }

  @Test void error_anonymous_message() {
    Tags.ERROR.tag(new RuntimeException("this cake is a lie") {}, span);

    verify(span).tag("error", "this cake is a lie");
  }

  TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).build();

  /** These are not good examples of actual baggage.. just to test the types. */
  @Test void baggageField() {
    Tags.BAGGAGE_FIELD.tag(BaggageFields.TRACE_ID, context, span);

    verify(span).tag("traceId", "0000000000000001");
  }

  @Test void baggageField_nullValue() {
    Tags.BAGGAGE_FIELD.tag(BaggageFields.SAMPLED, context, span);

    verifyNoMoreInteractions(span);
  }
}
