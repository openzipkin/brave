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
package brave.internal.handler;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.handler.SpanHandler.Cause;
import brave.propagation.TraceContext;
import java.util.concurrent.atomic.AtomicBoolean;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NoopAwareSpanHandlerTest {
  TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
  MutableSpan span = new MutableSpan();
  AtomicBoolean noop = new AtomicBoolean(false);

  @Mock SpanHandler one;
  @Mock SpanHandler two;
  @Mock SpanHandler three;

  @Test public void create_emptyIsNoop() {
    assertThat(NoopAwareSpanHandler.create(new SpanHandler[0], noop))
        .isEqualTo(SpanHandler.NOOP);
  }

  @Test public void create_single() {
    NoopAwareSpanHandler handler =
        (NoopAwareSpanHandler) NoopAwareSpanHandler.create(new SpanHandler[] {one}, noop);

    assertThat(handler.delegate).isSameAs(one);

    handler.end(context, span, Cause.FINISHED);
    verify(one).end(context, span, Cause.FINISHED);
  }

  @Test public void honorsNoop() {
    SpanHandler handler = NoopAwareSpanHandler.create(new SpanHandler[] {one}, noop);

    noop.set(true);

    handler.end(context, span, Cause.FINISHED);
    verify(one, never()).end(context, span, Cause.FINISHED);
  }

  @Test public void create_multiple() {
    SpanHandler[] handlers = new SpanHandler[2];
    handlers[0] = one;
    handlers[1] = two;
    SpanHandler handler = NoopAwareSpanHandler.create(handlers, noop);

    assertThat(handler).extracting("delegate.handlers")
        .asInstanceOf(InstanceOfAssertFactories.array(SpanHandler[].class))
        .containsExactly(one, two);
  }

  @Test public void multiple_callInSequence() {
    SpanHandler[] handlers = new SpanHandler[2];
    handlers[0] = one;
    handlers[1] = two;
    SpanHandler handler = NoopAwareSpanHandler.create(handlers, noop);
    when(one.begin(eq(context), eq(span), isNull())).thenReturn(true);
    when(one.end(eq(context), eq(span), eq(Cause.FINISHED))).thenReturn(true);

    handler.begin(context, span, null);
    handler.end(context, span, Cause.FINISHED);

    verify(one).begin(context, span, null);
    verify(two).begin(context, span, null);
    verify(two).end(context, span, Cause.FINISHED);
    verify(one).end(context, span, Cause.FINISHED);
  }

  @Test public void multiple_shortCircuitWhenFirstReturnsFalse() {
    SpanHandler[] handlers = new SpanHandler[2];
    handlers[0] = one;
    handlers[1] = two;
    SpanHandler handler = NoopAwareSpanHandler.create(handlers, noop);
    handler.end(context, span, Cause.FINISHED);

    verify(one).end(context, span, Cause.FINISHED);
    verify(two, never()).end(context, span, Cause.FINISHED);
  }

  @Test public void multiple_abandoned() {
    SpanHandler[] handlers = new SpanHandler[3];
    handlers[0] = one;
    handlers[1] = two;
    handlers[2] = three;

    when(two.handlesAbandoned()).thenReturn(true);

    SpanHandler handler = NoopAwareSpanHandler.create(handlers, noop);
    assertThat(handler.handlesAbandoned()).isTrue();
    handler.end(context, span, Cause.ABANDONED);

    verify(one, never()).end(context, span, Cause.ABANDONED);
    verify(two).end(context, span, Cause.ABANDONED);
    verify(three, never()).end(context, span, Cause.FINISHED);
  }

  @Test public void doesntCrashOnNonFatalThrowable() {
    Throwable[] toThrow = new Throwable[1];
    SpanHandler handler =
        NoopAwareSpanHandler.create(new SpanHandler[] {new SpanHandler() {
          @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
            doThrowUnsafely(toThrow[0]);
            return true;
          }
        }}, noop);

    toThrow[0] = new RuntimeException();
    assertThat(handler.end(context, span, Cause.FINISHED)).isTrue();

    toThrow[0] = new Exception();
    assertThat(handler.end(context, span, Cause.FINISHED)).isTrue();

    toThrow[0] = new Error();
    assertThat(handler.end(context, span, Cause.FINISHED)).isTrue();

    toThrow[0] = new StackOverflowError(); // fatal
    try { // assertThatThrownBy doesn't work with StackOverflowError
      handler.end(context, span, Cause.FINISHED);
      failBecauseExceptionWasNotThrown(StackOverflowError.class);
    } catch (StackOverflowError e) {
    }
  }

  // Trick from Armeria: This black magic causes the Java compiler to believe E is unchecked.
  static <E extends Throwable> void doThrowUnsafely(Throwable cause) throws E {
    throw (E) cause;
  }
}
