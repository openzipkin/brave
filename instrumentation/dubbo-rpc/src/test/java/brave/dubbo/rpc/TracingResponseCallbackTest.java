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
package brave.dubbo.rpc;

import brave.Span;
import brave.sampler.Sampler;
import com.alibaba.dubbo.remoting.exchange.ResponseCallback;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TracingResponseCallbackTest extends ITTracingFilter {
  @Before public void setup() {
    setTracing(tracingBuilder(Sampler.ALWAYS_SAMPLE).build());
  }

  @Test public void done_should_finish_span() throws Exception {
    Span span = tracing.tracer().nextSpan().start();

    ResponseCallback tracingResponseCallback =
      TracingResponseCallback.create(null, span, tracing.currentTraceContext());
    tracingResponseCallback.done(null);

    assertThat(spans.take()).isNotNull();
  }

  @Test public void caught_should_tag() throws Exception {
    Span span = tracing.tracer().nextSpan().start();

    ResponseCallback tracingResponseCallback =
      TracingResponseCallback.create(null, span, tracing.currentTraceContext());
    tracingResponseCallback.caught(new Exception("Test exception"));

    assertThat(spans.take().tags())
      .containsEntry("error", "Test exception");
  }

  @Test public void done_should_forward_then_finish_span() throws Exception {
    Span span = tracing.tracer().nextSpan().start();

    ResponseCallback delegate = mock(ResponseCallback.class);
    ResponseCallback tracingResponseCallback =
      TracingResponseCallback.create(delegate, span, tracing.currentTraceContext());

    Object result = new Object();
    tracingResponseCallback.done(result);

    verify(delegate).done(result);
    assertThat(spans.take()).isNotNull();
  }

  @Test public void done_should_have_span_in_scope() throws Exception {
    Span span = tracing.tracer().nextSpan().start();

    ResponseCallback delegate = new ResponseCallback() {
      @Override public void done(Object response) {
        assertThat(tracing.currentTraceContext().get()).isSameAs(span.context());
      }

      @Override public void caught(Throwable exception) {
        throw new AssertionError();
      }
    };

    TracingResponseCallback.create(delegate, span, tracing.currentTraceContext())
      .done(new Object());

    assertThat(spans.take()).isNotNull();
  }

  @Test public void caught_should_forward_then_tag() throws Exception {
    Span span = tracing.tracer().nextSpan().start();

    ResponseCallback delegate = new ResponseCallback() {
      @Override public void done(Object response) {
        throw new AssertionError();
      }

      @Override public void caught(Throwable exception) {
        assertThat(tracing.currentTraceContext().get()).isSameAs(span.context());
      }
    };

    TracingResponseCallback.create(delegate, span, tracing.currentTraceContext())
      .caught(new Exception("Test exception"));

    assertThat(spans.take().tags())
      .containsEntry("error", "Test exception");
  }
}
