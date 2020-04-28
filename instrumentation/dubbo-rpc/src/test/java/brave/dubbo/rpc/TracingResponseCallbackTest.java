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
package brave.dubbo.rpc;

import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import com.alibaba.dubbo.remoting.exchange.ResponseCallback;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TracingResponseCallbackTest {
  FinishSpan finishSpan = mock(FinishSpan.class);
  TraceContext invocationContext = TraceContext.newBuilder().traceId(1).spanId(2).build();
  CurrentTraceContext currentTraceContext = ThreadLocalCurrentTraceContext.create();

  @Test public void done_should_finish_span() {
    ResponseCallback callback =
      TracingResponseCallback.create(null, finishSpan, currentTraceContext, invocationContext);

    callback.done(null);

    verify(finishSpan).accept(null, null);
  }

  @Test public void done_should_finish_span_caught() {
    ResponseCallback callback =
      TracingResponseCallback.create(null, finishSpan, currentTraceContext, invocationContext);

    Throwable error = new Exception("Test exception");
    callback.caught(error);

    verify(finishSpan).accept(null, error);
  }

  @Test public void done_should_forward_then_finish_span() {
    ResponseCallback delegate = mock(ResponseCallback.class);

    ResponseCallback callback =
      TracingResponseCallback.create(delegate, finishSpan, currentTraceContext, invocationContext);

    Object result = new Object();
    callback.done(result);

    verify(delegate).done(result);
    verify(finishSpan).accept(result, null);
  }

  @Test public void done_should_have_span_in_scope() {
    ResponseCallback delegate = new ResponseCallback() {
      @Override public void done(Object response) {
        assertThat(currentTraceContext.get()).isSameAs(invocationContext);
      }

      @Override public void caught(Throwable exception) {
        throw new AssertionError();
      }
    };

    ResponseCallback callback =
      TracingResponseCallback.create(delegate, finishSpan, currentTraceContext, invocationContext);

    Object result = new Object();
    callback.done(result);

    verify(finishSpan).accept(result, null);
  }

  @Test public void done_should_have_span_in_scope_caught() {
    ResponseCallback delegate = new ResponseCallback() {
      @Override public void done(Object response) {
        throw new AssertionError();
      }

      @Override public void caught(Throwable exception) {
        assertThat(currentTraceContext.get()).isSameAs(invocationContext);
      }
    };

    ResponseCallback callback =
      TracingResponseCallback.create(delegate, finishSpan, currentTraceContext, invocationContext);

    Throwable error = new Exception("Test exception");
    callback.caught(error);

    verify(finishSpan).accept(null, error);
  }
}
