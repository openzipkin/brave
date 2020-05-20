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
package brave.test;

import brave.Span;
import brave.handler.MutableSpan;
import brave.propagation.TraceContext;
import org.junit.Test;

import static brave.Span.Kind.CLIENT;
import static brave.handler.SpanHandler.Cause.FINISHED;
import static brave.handler.SpanHandler.Cause.ORPHANED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IntegrationTestSpanHandlerTest {
  IntegrationTestSpanHandler spanHandler = new IntegrationTestSpanHandler();
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(2L).build();
  MutableSpan span = new MutableSpan(context, null);

  @Test public void goodMessageForUnstartedSpan() {
    spanHandler.end(context, span, FINISHED); // NOT ORPHANED!

    assertThatThrownBy(spanHandler::takeLocalSpan)
        .hasMessage(
            "Expected a startTimestamp: {\"traceId\":\"0000000000000001\",\"id\":\"0000000000000002\"}\n"
                + "Look for code missing span.start().");
  }

  @Test public void goodMessageForOrphanedSpan() {
    spanHandler.begin(context, span, null);
    spanHandler.end(context, span, ORPHANED);

    assertThatThrownBy(spanHandler::takeLocalSpan)
        .hasMessageStartingWith("Orphaned span found")
        .hasMessageContaining("brave.flush")
        .hasMessageEndingWith("Look for code missing span.flush() or span.finish().");
  }

  @Test public void toString_includesSpans() {
    spanHandler.end(context, span, FINISHED);

    assertThat(spanHandler)
        .hasToString("[{\"traceId\":\"0000000000000001\",\"id\":\"0000000000000002\"}]");
  }

  /** Shows the argument is a pattern, not equals */
  @Test public void takeRemoteSpanWithErrorMessage_pattern() {
    span.kind(CLIENT);
    span.startTimestamp(1L);
    span.error(new RuntimeException("ice ice baby"));
    span.finishTimestamp(2L);
    spanHandler.end(context, span, FINISHED);

    assertThat(spanHandler.takeRemoteSpanWithErrorMessage(CLIENT, ".* ice.*")).isSameAs(span);
  }

  /**
   * Some exceptions, like Dubbo, are multi-line where the line of interest isn't even always on the
   * last line.
   */
  @Test public void takeRemoteSpanWithErrorMessage_multiline() {
    span.kind(CLIENT);
    span.startTimestamp(1L);
    span.error(new RuntimeException("ice ice baby\nvanilla\nice ice baby"));
    span.finishTimestamp(2L);
    spanHandler.end(context, span, FINISHED);

    assertThat(spanHandler.takeRemoteSpanWithErrorMessage(CLIENT, ".*vanilla.*")).isSameAs(span);
  }

  /**
   * When we don't know the error message, or the message can be {@code null}, we should use {@link
   * IntegrationTestSpanHandler#takeRemoteSpanWithError(Span.Kind)}, not {@link
   * IntegrationTestSpanHandler#takeRemoteSpanWithErrorMessage(Span.Kind, String)} (regex).
   */
  @Test public void takeRemoteSpanWithErrorMessage_null_notOk() {
    span.kind(CLIENT);
    span.startTimestamp(1L);
    span.error(new RuntimeException());
    span.finishTimestamp(2L);
    spanHandler.end(context, span, FINISHED);

    assertThatThrownBy(() -> spanHandler.takeRemoteSpanWithErrorMessage(CLIENT, ".+"))
        .isInstanceOf(AssertionError.class)
        .hasMessageEndingWith("to have an error message matching [.+], but was [null]");
  }

  @Test public void takeRemoteSpanWithError_nullMessage() {
    span.kind(CLIENT);
    span.startTimestamp(1L);
    span.error(new RuntimeException());
    span.finishTimestamp(2L);
    spanHandler.end(context, span, FINISHED);

    assertThat(spanHandler.takeRemoteSpanWithError(CLIENT)).isSameAs(span);
  }
}
