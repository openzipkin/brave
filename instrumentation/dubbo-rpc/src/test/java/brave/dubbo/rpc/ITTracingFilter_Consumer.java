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

import brave.Clock;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ReferenceConfig;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ITTracingFilter_Consumer extends ITTracingFilter {

  @Before public void setup() {
    init();
    server.start();

    client = new ReferenceConfig<>();
    client.setApplication(new ApplicationConfig("bean-consumer"));
    client.setFilter("tracing");
    client.setInterface(GreeterService.class);
    client.setUrl("dubbo://" + server.ip() + ":" + server.port() + "?scope=remote&generic=bean");
  }

  @Test public void propagatesNewTrace() {
    client.get().sayHello("jorge");

    TraceContext extracted = server.takeRequest().context();
    assertThat(extracted.sampled()).isTrue();
    assertThat(extracted.parentIdString()).isNull();
    assertSameIds(reporter.takeRemoteSpan(Span.Kind.CLIENT), extracted);
  }

  @Test public void propagatesChildOfCurrentSpan() {
    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      client.get().sayHello("jorge");
    }

    TraceContext extracted = server.takeRequest().context();
    assertThat(extracted.sampled()).isTrue();
    assertChildOf(extracted, parent);
    assertSameIds(reporter.takeRemoteSpan(Span.Kind.CLIENT), extracted);
  }

  /** Unlike Brave 3, Brave 4 propagates trace ids even when unsampled */
  @Test public void propagatesUnsampledContext() {
    TraceContext parent = newTraceContext(SamplingFlags.NOT_SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      client.get().sayHello("jorge");
    }

    TraceContext extracted = server.takeRequest().context();
    assertThat(extracted.sampled()).isFalse();
    assertChildOf(extracted, parent);
  }

  @Test public void propagatesBaggage() {
    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      BAGGAGE_FIELD.updateValue(parent, "joey");
      client.get().sayHello("jorge");
    }

    TraceContext extracted = server.takeRequest().context();
    assertThat(BAGGAGE_FIELD.getValue(extracted)).isEqualTo("joey");

    reporter.takeRemoteSpan(Span.Kind.CLIENT);
  }

  @Test public void propagatesBaggage_unsampled() {
    TraceContext parent = newTraceContext(SamplingFlags.NOT_SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      BAGGAGE_FIELD.updateValue(parent, "joey");
      client.get().sayHello("jorge");
    }

    TraceContext extracted = server.takeRequest().context();
    assertThat(BAGGAGE_FIELD.getValue(extracted)).isEqualTo("joey");
  }

  /** This prevents confusion as a blocking client should end before, the start of the next span. */
  @Test public void clientTimestampAndDurationEnclosedByParent() {
    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    Clock clock = tracing.clock(parent);

    long start = clock.currentTimeMicroseconds();
    try (Scope scope = currentTraceContext.newScope(parent)) {
      client.get().sayHello("jorge");
    }
    long finish = clock.currentTimeMicroseconds();

    Span clientSpan = reporter.takeRemoteSpan(Span.Kind.CLIENT);
    assertChildOf(clientSpan, parent);
    assertSpanInInterval(clientSpan, start, finish);
  }

  /**
   * This tests that the parent is determined at the time the request was made, not when the request
   * was executed.
   */
  @Test public void usesParentFromInvocationTime() {
    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      RpcContext.getContext().asyncCall(() -> client.get().sayHello("jorge"));
      RpcContext.getContext().asyncCall(() -> client.get().sayHello("romeo"));
    }

    try (Scope scope = currentTraceContext.newScope(null)) {
      // complete within a different scope
      for (int i = 0; i < 2; i++) {
        TraceContext extracted = server.takeRequest().context();
        assertChildOf(extracted, parent);
      }
    }

    // The spans may report in a different order than the requests
    for (int i = 0; i < 2; i++) {
      assertChildOf(reporter.takeRemoteSpan(Span.Kind.CLIENT), parent);
    }
  }

  @Test public void reportsClientKindToZipkin() {
    client.get().sayHello("jorge");

    reporter.takeRemoteSpan(Span.Kind.CLIENT);
  }

  @Test public void defaultSpanNameIsMethodName() {
    client.get().sayHello("jorge");

    assertThat(reporter.takeRemoteSpan(Span.Kind.CLIENT).name())
      .isEqualTo("brave.dubbo.rpc.greeterservice/sayhello");
  }

  @Test public void onTransportException_addsErrorTag() {
    server.stop();

    assertThatThrownBy(() -> client.get().sayHello("jorge"))
      .isInstanceOf(RpcException.class);

    reporter.takeRemoteSpanWithError(Span.Kind.CLIENT, ".*RemotingException.*");
  }

  @Test public void onTransportException_addsErrorTag_async() {
    server.stop();

    RpcContext.getContext().asyncCall(() -> client.get().sayHello("romeo"));

    reporter.takeRemoteSpanWithError(Span.Kind.CLIENT, ".*RemotingException.*");
  }

  @Test public void finishesOneWay() {
    RpcContext.getContext().asyncCall(() -> {
      client.get().sayHello("romeo");
    });

    reporter.takeRemoteSpan(Span.Kind.CLIENT);
  }

  @Test public void addsErrorTag_onUnimplemented() {
    server.stop();
    server = new TestServer(propagationFactory);
    server.service.setRef((method, parameterTypes, args) -> args);
    server.start();

    assertThatThrownBy(() -> client.get().sayHello("jorge"))
      .isInstanceOf(RpcException.class);

    Span span =
      reporter.takeRemoteSpanWithError(Span.Kind.CLIENT, ".*Not found exported service.*");
    assertThat(span.tags().get("dubbo.error_code")).isEqualTo("1");
  }

  /** Ensures the span completes on asynchronous invocation. */
  @Test public void test_async_invoke() throws Exception {
    client.setAsync(true);
    String jorge = client.get().sayHello("jorge");
    assertThat(jorge).isNull();
    Object o = RpcContext.getContext().getFuture().get();
    assertThat(o).isNotNull();

    reporter.takeRemoteSpan(Span.Kind.CLIENT);
  }
}
