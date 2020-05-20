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
package brave;

import brave.handler.MutableSpan;
import brave.propagation.TraceContext;
import java.util.function.BiFunction;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class TagTest {
  Span span = mock(Span.class);
  ScopedSpan scopedSpan = mock(ScopedSpan.class);
  SpanCustomizer customizer = mock(SpanCustomizer.class);
  MutableSpan mutableSpan = new MutableSpan();
  BiFunction<Object, TraceContext, String> parseValue = mock(BiFunction.class);

  Object input = new Object();
  TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).build();
  Tag<Object> tag = new Tag<Object>("key") {
    @Override protected String parseValue(Object input, TraceContext context) {
      return parseValue.apply(input, context);
    }
  };

  @Before public void setup() {
    when(span.context()).thenReturn(context);
    when(scopedSpan.context()).thenReturn(context);
  }

  @Test public void trimsKey() {
    assertThat(new Tag<Object>(" x-foo  ") {
      @Override protected String parseValue(Object input, TraceContext context) {
        return null;
      }
    }.key()).isEqualTo("x-foo");
  }

  @Test public void key_invalid() {
    assertThatThrownBy(() -> new Tag<Object>(null) {
      @Override protected String parseValue(Object input, TraceContext context) {
        return null;
      }
    }).isInstanceOf(NullPointerException.class);

    assertThatThrownBy(() -> new Tag<Object>("") {
      @Override protected String parseValue(Object input, TraceContext context) {
        return null;
      }
    }).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new Tag<Object>("   ") {
      @Override protected String parseValue(Object input, TraceContext context) {
        return null;
      }
    }).isInstanceOf(IllegalArgumentException.class);
  }

  @Test public void tag_span() {
    when(parseValue.apply(input, context)).thenReturn("value");

    tag.tag(input, span);

    verify(span).context();
    verify(span).isNoop();
    verify(parseValue).apply(input, context);
    verifyNoMoreInteractions(parseValue); // doesn't parse twice
    verify(span).tag("key", "value");
    verifyNoMoreInteractions(span); // doesn't tag twice
  }

  @Test public void tag_span_empty() {
    when(parseValue.apply(input, context)).thenReturn("");

    tag.tag(input, span);

    verify(span).context();
    verify(span).isNoop();
    verify(parseValue).apply(input, context);
    verifyNoMoreInteractions(parseValue); // doesn't parse twice
    verify(span).tag("key", "");
    verifyNoMoreInteractions(span); // doesn't tag twice
  }

  @Test public void tag_span_doesntParseNoop() {
    when(span.isNoop()).thenReturn(true);

    verifyNoMoreInteractions(parseValue); // parsing is lazy
    verifyNoMoreInteractions(span);
  }

  @Test public void tag_span_ignoredErrorParsing() {
    when(parseValue.apply(input, context)).thenThrow(new Error());

    tag.tag(input, span);

    verify(span).context();
    verify(span).isNoop();
    verify(parseValue).apply(input, context);
    verifyNoMoreInteractions(parseValue); // doesn't parse twice
    verifyNoMoreInteractions(span);
  }

  @Test public void tag_scopedSpan() {
    when(parseValue.apply(input, context)).thenReturn("value");

    tag.tag(input, scopedSpan);

    verify(scopedSpan).isNoop();
    verify(scopedSpan).context();
    verify(parseValue).apply(input, context);
    verifyNoMoreInteractions(parseValue); // doesn't parse twice
    verify(scopedSpan).tag("key", "value");
    verifyNoMoreInteractions(scopedSpan); // doesn't tag twice
  }

  @Test public void tag_scopedSpan_empty() {
    when(parseValue.apply(input, context)).thenReturn("");

    tag.tag(input, scopedSpan);

    verify(scopedSpan).isNoop();
    verify(scopedSpan).context();
    verify(parseValue).apply(input, context);
    verifyNoMoreInteractions(parseValue); // doesn't parse twice
    verify(scopedSpan).tag("key", "");
    verifyNoMoreInteractions(scopedSpan); // doesn't tag twice
  }

  @Test public void tag_scopedSpan_doesntParseNoop() {
    when(scopedSpan.isNoop()).thenReturn(true);

    verifyNoMoreInteractions(parseValue); // parsing is lazy
    verifyNoMoreInteractions(scopedSpan);
  }

  @Test public void tag_scopedSpan_ignoredErrorParsing() {
    when(parseValue.apply(input, context)).thenThrow(new Error());

    tag.tag(input, scopedSpan);

    verify(scopedSpan).isNoop();
    verify(scopedSpan).context();
    verify(parseValue).apply(input, context);
    verifyNoMoreInteractions(parseValue); // doesn't parse twice
    verifyNoMoreInteractions(scopedSpan);
  }

  @Test public void tag_customizer() {
    when(parseValue.apply(input, null)).thenReturn("value");

    tag.tag(input, customizer);

    verify(parseValue).apply(input, null);
    verifyNoMoreInteractions(parseValue); // doesn't parse twice
    verify(customizer).tag("key", "value");
    verifyNoMoreInteractions(customizer); // doesn't tag twice
  }

  @Test public void tag_customizer_empty() {
    when(parseValue.apply(input, null)).thenReturn("");

    tag.tag(input, customizer);

    verify(parseValue).apply(input, null);
    verifyNoMoreInteractions(parseValue); // doesn't parse twice
    verify(customizer).tag("key", "");
    verifyNoMoreInteractions(customizer); // doesn't tag twice
  }

  @Test public void tag_customizer_doesntParseNoop() {
    tag.tag(input, context, NoopSpanCustomizer.INSTANCE);

    verifyNoMoreInteractions(parseValue); // parsing is lazy
  }

  @Test public void tag_customizer_ignoredErrorParsing() {
    when(parseValue.apply(input, null)).thenThrow(new Error());

    tag.tag(input, customizer);

    verify(parseValue).apply(input, null);
    verifyNoMoreInteractions(parseValue); // doesn't parse twice
    verifyNoMoreInteractions(customizer);
  }

  @Test public void tag_customizer_withNullContext() {
    when(parseValue.apply(eq(input), isNull())).thenReturn("value");

    tag.tag(input, null, customizer);

    verify(parseValue).apply(input, null);
    verifyNoMoreInteractions(parseValue); // doesn't parse twice
    verify(customizer).tag("key", "value");
    verifyNoMoreInteractions(customizer); // doesn't tag twice
  }

  @Test public void tag_customizer_withContext() {
    when(parseValue.apply(input, context)).thenReturn("value");

    tag.tag(input, context, customizer);

    verify(parseValue).apply(input, context);
    verifyNoMoreInteractions(parseValue); // doesn't parse twice
    verify(customizer).tag("key", "value");
    verifyNoMoreInteractions(customizer); // doesn't tag twice
  }

  @Test public void tag_customizer_withContext_empty() {
    when(parseValue.apply(input, context)).thenReturn("");

    tag.tag(input, context, customizer);

    verify(parseValue).apply(input, context);
    verifyNoMoreInteractions(parseValue); // doesn't parse twice
    verify(customizer).tag("key", "");
    verifyNoMoreInteractions(customizer); // doesn't tag twice
  }

  @Test public void tag_customizer_withContext_doesntParseNoop() {
    tag.tag(input, context, NoopSpanCustomizer.INSTANCE);

    verifyNoMoreInteractions(parseValue); // parsing is lazy
  }

  @Test public void tag_customizer_withContext_ignoredErrorParsing() {
    when(parseValue.apply(input, context)).thenThrow(new Error());

    tag.tag(input, context, customizer);

    verify(parseValue).apply(input, context);
    verifyNoMoreInteractions(parseValue); // doesn't parse twice
    verifyNoMoreInteractions(customizer);
  }

  @Test public void tag_mutableSpan() {
    when(parseValue.apply(input, context)).thenReturn("value");

    tag.tag(input, context, mutableSpan);

    verify(parseValue).apply(input, context);
    verifyNoMoreInteractions(parseValue); // doesn't parse twice

    MutableSpan expected = new MutableSpan();
    expected.tag("key", "value");
    assertThat(mutableSpan).isEqualTo(expected);
  }

  @Test public void tag_mutableSpan_nullContext() {
    when(parseValue.apply(eq(input), isNull())).thenReturn("value");

    tag.tag(input, null, mutableSpan);

    verify(parseValue).apply(input, null);
    verifyNoMoreInteractions(parseValue); // doesn't parse twice

    MutableSpan expected = new MutableSpan();
    expected.tag("key", "value");
    assertThat(mutableSpan).isEqualTo(expected);
  }

  @Test public void tag_mutableSpan_empty() {
    when(parseValue.apply(input, context)).thenReturn("");

    tag.tag(input, context, mutableSpan);

    verify(parseValue).apply(input, context);
    verifyNoMoreInteractions(parseValue); // doesn't parse twice

    MutableSpan expected = new MutableSpan();
    expected.tag("key", "");
    assertThat(mutableSpan).isEqualTo(expected);
  }

  @Test public void tag_mutableSpan_ignoredErrorParsing() {
    when(parseValue.apply(input, context)).thenThrow(new Error());

    tag.tag(input, context, mutableSpan);

    verify(parseValue).apply(input, context);
    verifyNoMoreInteractions(parseValue); // doesn't parse twice

    assertThat(mutableSpan.isEmpty()).isTrue();
  }
}
