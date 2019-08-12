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
package brave.jms;

import brave.Span;
import brave.sampler.Sampler;
import javax.jms.CompletionListener;
import javax.jms.Message;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TracingCompletionListenerTest extends JmsTest {
  @Test public void create_returns_input_on_noop() {
    Span span = tracing.tracer().withSampler(Sampler.NEVER_SAMPLE).nextSpan();

    CompletionListener delegate = mock(CompletionListener.class);
    CompletionListener tracingCompletionListener =
      TracingCompletionListener.create(delegate, span, current);

    assertThat(tracingCompletionListener).isSameAs(delegate);
  }

  @Test public void on_completion_should_finish_span() throws Exception {
    Message message = mock(Message.class);
    Span span = tracing.tracer().nextSpan().start();

    CompletionListener tracingCompletionListener =
      TracingCompletionListener.create(mock(CompletionListener.class), span, current);
    tracingCompletionListener.onCompletion(message);

    assertThat(takeSpan()).isNotNull();
  }

  @Test public void on_exception_should_tag_if_exception() throws Exception {
    Message message = mock(Message.class);
    Span span = tracing.tracer().nextSpan().start();

    CompletionListener tracingCompletionListener =
      TracingCompletionListener.create(mock(CompletionListener.class), span, current);
    tracingCompletionListener.onException(message, new Exception("Test exception"));

    assertThat(takeSpan().tags())
      .containsEntry("error", "Test exception");
  }

  @Test public void on_completion_should_forward_then_finish_span() throws Exception {
    Message message = mock(Message.class);
    Span span = tracing.tracer().nextSpan().start();

    CompletionListener delegate = mock(CompletionListener.class);
    CompletionListener tracingCompletionListener =
      TracingCompletionListener.create(delegate, span, current);
    tracingCompletionListener.onCompletion(message);

    verify(delegate).onCompletion(message);
    assertThat(takeSpan()).isNotNull();
  }

  @Test public void on_completion_should_have_span_in_scope() throws Exception {
    Message message = mock(Message.class);
    Span span = tracing.tracer().nextSpan().start();

    CompletionListener delegate = new CompletionListener() {
      @Override public void onCompletion(Message message) {
        assertThat(current.get()).isSameAs(span.context());
      }

      @Override public void onException(Message message, Exception exception) {
        throw new AssertionError();
      }
    };

    TracingCompletionListener.create(delegate, span, current).onCompletion(message);

    takeSpan(); // consumer reported span
  }

  @Test public void on_exception_should_forward_then_tag() throws Exception {
    Message message = mock(Message.class);
    Span span = tracing.tracer().nextSpan().start();

    CompletionListener delegate = mock(CompletionListener.class);
    CompletionListener tracingCompletionListener =
      TracingCompletionListener.create(delegate, span, current);
    Exception e = new Exception("Test exception");
    tracingCompletionListener.onException(message, e);

    verify(delegate).onException(message, e);
    assertThat(takeSpan().tags())
      .containsEntry("error", "Test exception");
  }
}
