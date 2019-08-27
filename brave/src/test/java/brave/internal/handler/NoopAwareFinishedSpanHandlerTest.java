/*
 * Copyright 2013-2019 The OpenZipkin Authors
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

import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.propagation.TraceContext;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NoopAwareFinishedSpanHandlerTest {
  TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
  MutableSpan span = new MutableSpan();
  AtomicBoolean noop = new AtomicBoolean(false);

  @Mock FinishedSpanHandler one;
  @Mock FinishedSpanHandler two;

  @Test public void create_emptyIsNoop() {
    assertThat(NoopAwareFinishedSpanHandler.create(asList(), noop))
      .isEqualTo(FinishedSpanHandler.NOOP);
  }

  @Test public void create_noopPassthrough() {
    assertThat(NoopAwareFinishedSpanHandler.create(asList(FinishedSpanHandler.NOOP), noop))
      .isEqualTo(FinishedSpanHandler.NOOP);
  }

  @Test public void create_single() {
    FinishedSpanHandler handler = NoopAwareFinishedSpanHandler.create(asList(one), noop);

    assertThat(handler)
      .isInstanceOf(NoopAwareFinishedSpanHandler.Single.class);

    handler.handle(context, span);
    verify(one).handle(context, span);
  }

  @Test public void honorsNoop() {
    FinishedSpanHandler handler = NoopAwareFinishedSpanHandler.create(asList(one), noop);

    noop.set(true);

    handler.handle(context, span);
    verify(one, never()).handle(context, span);
  }

  @Test public void single_options() {
    assertThat(NoopAwareFinishedSpanHandler.create(asList(one), noop))
      .extracting(FinishedSpanHandler::alwaysSampleLocal, FinishedSpanHandler::supportsOrphans)
      .containsExactly(false, false);

    when(one.alwaysSampleLocal()).thenReturn(true);
    when(one.supportsOrphans()).thenReturn(true);

    assertThat(NoopAwareFinishedSpanHandler.create(asList(one), noop))
      .extracting(FinishedSpanHandler::alwaysSampleLocal, FinishedSpanHandler::supportsOrphans)
      .containsExactly(true, true);
  }

  @Test public void create_multiple() {
    FinishedSpanHandler handler = NoopAwareFinishedSpanHandler.create(asList(one, two), noop);

    assertThat(handler)
      .isInstanceOf(NoopAwareFinishedSpanHandler.Multiple.class);
  }

  @Test public void multiple_options() {
    assertThat(NoopAwareFinishedSpanHandler.create(asList(one, two), noop))
      .extracting(FinishedSpanHandler::alwaysSampleLocal, FinishedSpanHandler::supportsOrphans)
      .containsExactly(false, false);

    when(one.alwaysSampleLocal()).thenReturn(true);
    when(one.supportsOrphans()).thenReturn(true);
    when(two.alwaysSampleLocal()).thenReturn(true);
    when(two.supportsOrphans()).thenReturn(true);

    assertThat(NoopAwareFinishedSpanHandler.create(asList(one, two), noop))
      .extracting(FinishedSpanHandler::alwaysSampleLocal, FinishedSpanHandler::supportsOrphans)
      .containsExactly(true, true);
  }

  @Test public void multiple_callInSequence() {
    FinishedSpanHandler handler = NoopAwareFinishedSpanHandler.create(asList(one, two), noop);
    when(one.handle(context, span)).thenReturn(true);
    handler.handle(context, span);

    verify(one).handle(context, span);
    verify(two).handle(context, span);
  }

  @Test public void multiple_shortCircuitWhenFirstReturnsFalse() {
    FinishedSpanHandler handler = NoopAwareFinishedSpanHandler.create(asList(one, two), noop);
    handler.handle(context, span);

    verify(one).handle(context, span);
    verify(two, never()).handle(context, span);
  }

  @Test public void doesntCrashOnNonFatalThrowable() {
    Throwable[] toThrow = new Throwable[1];
    FinishedSpanHandler handler =
      NoopAwareFinishedSpanHandler.create(asList(new FinishedSpanHandler() {
        @Override public boolean handle(TraceContext context, MutableSpan span) {
          doThrowUnsafely(toThrow[0]);
          return true;
        }
      }), noop);

    toThrow[0] = new RuntimeException();
    assertThat(handler.handle(context, span)).isFalse();

    toThrow[0] = new Exception();
    assertThat(handler.handle(context, span)).isFalse();

    toThrow[0] = new Error();
    assertThat(handler.handle(context, span)).isFalse();

    toThrow[0] = new StackOverflowError(); // fatal
    try { // assertThatThrownBy doesn't work with StackOverflowError
      handler.handle(context, span);
      failBecauseExceptionWasNotThrown(StackOverflowError.class);
    } catch (StackOverflowError e) {
    }
  }

  // Trick from Armeria: This black magic causes the Java compiler to believe E is unchecked.
  static <E extends Throwable> void doThrowUnsafely(Throwable cause) throws E {
    throw (E) cause;
  }
}
