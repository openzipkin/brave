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
package brave.dubbo;

import brave.Clock;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.rpc.RpcRuleSampler;
import brave.rpc.RpcTracing;
import brave.test.util.AssertableCallback;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Span;

import static brave.rpc.RpcRequestMatchers.methodEquals;
import static brave.rpc.RpcRequestMatchers.serviceEquals;
import static brave.sampler.Sampler.ALWAYS_SAMPLE;
import static brave.sampler.Sampler.NEVER_SAMPLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ITTracingFilter_Consumer extends ITTracingFilter {
  ReferenceConfig<GraterService> wrongClient;

  @Before public void setup() throws Exception {
    server.start();

    String url = "dubbo://" + server.ip() + ":" + server.port() + "?scope=remote&generic=bean";
    client = new ReferenceConfig<>();
    client.setApplication(application);
    client.setFilter("tracing");
    client.setInterface(GreeterService.class);
    client.setUrl(url);

    wrongClient = new ReferenceConfig<>();
    wrongClient.setApplication(application);
    wrongClient.setFilter("tracing");
    wrongClient.setInterface(GraterService.class);
    wrongClient.setUrl(url);

    init();

    // perform a warmup request to allow CI to fail quicker
    client.get().sayHello("jorge");
    server.takeRequest();
    takeRemoteSpan(Span.Kind.CLIENT);
  }

  @After public void stop() {
    if (wrongClient != null) wrongClient.destroy();
    super.stop();
  }

  @Test public void propagatesNewTrace() throws Exception {
    client.get().sayHello("jorge");

    TraceContext extracted = server.takeRequest().context();
    assertThat(extracted.sampled()).isTrue();
    assertThat(extracted.parentIdString()).isNull();
    assertSameIds(takeRemoteSpan(Span.Kind.CLIENT), extracted);
  }

  @Test public void propagatesChildOfCurrentSpan() throws Exception {
    TraceContext parent = newParentContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      client.get().sayHello("jorge");
    }

    TraceContext extracted = server.takeRequest().context();
    assertThat(extracted.sampled()).isTrue();
    assertChildOf(extracted, parent);
    assertSameIds(takeRemoteSpan(Span.Kind.CLIENT), extracted);
  }

  /** Unlike Brave 3, Brave 4 propagates trace ids even when unsampled */
  @Test public void propagatesUnsampledContext() throws Exception {
    TraceContext parent = newParentContext(SamplingFlags.NOT_SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      client.get().sayHello("jorge");
    }

    TraceContext extracted = server.takeRequest().context();
    assertThat(extracted.sampled()).isFalse();
    assertChildOf(extracted, parent);
  }

  @Test public void propagatesExtra() throws Exception {
    TraceContext parent = newParentContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      ExtraFieldPropagation.set(parent, EXTRA_KEY, "joey");
      client.get().sayHello("jorge");
    }

    TraceContext extracted = server.takeRequest().context();
    assertThat(ExtraFieldPropagation.get(extracted, EXTRA_KEY)).isEqualTo("joey");

    takeRemoteSpan(Span.Kind.CLIENT);
  }

  @Test public void propagatesExtra_unsampled() throws Exception {
    TraceContext parent = newParentContext(SamplingFlags.NOT_SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      ExtraFieldPropagation.set(parent, EXTRA_KEY, "joey");
      client.get().sayHello("jorge");
    }

    TraceContext extracted = server.takeRequest().context();
    assertThat(ExtraFieldPropagation.get(extracted, EXTRA_KEY)).isEqualTo("joey");
  }

  /** This prevents confusion as a blocking client should end before, the start of the next span. */
  @Test public void clientTimestampAndDurationEnclosedByParent() throws Exception {
    TraceContext parent = newParentContext(SamplingFlags.SAMPLED);
    Clock clock = tracing.clock(parent);

    long start = clock.currentTimeMicroseconds();
    try (Scope scope = currentTraceContext.newScope(parent)) {
      client.get().sayHello("jorge");
    }
    long finish = clock.currentTimeMicroseconds();

    Span clientSpan = takeRemoteSpan(Span.Kind.CLIENT);
    assertChildOf(clientSpan, parent);
    assertSpanInInterval(clientSpan, start, finish);
  }

  /**
   * This tests that the parent is determined at the time the request was made, not when the request
   * was executed.
   */
  @Test public void usesParentFromInvocationTime() throws Exception {
    AssertableCallback<String> items1 = new AssertableCallback<>();
    AssertableCallback<String> items2 = new AssertableCallback<>();

    TraceContext parent = newParentContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      RpcContext.getContext().asyncCall(() -> client.get().sayHello("jorge"))
        .whenComplete(items1);
      RpcContext.getContext().asyncCall(() -> client.get().sayHello("romeo"))
        .whenComplete(items2);
    }

    try (Scope scope = currentTraceContext.newScope(null)) {
      // complete within a different scope
      items1.assertThatSuccess().isNotNull();
      items2.assertThatSuccess().isNotNull();

      for (int i = 0; i < 2; i++) {
        TraceContext extracted = server.takeRequest().context();
        assertChildOf(extracted, parent);
      }
    }

    // The spans may report in a different order than the requests
    for (int i = 0; i < 2; i++) {
      assertChildOf(takeRemoteSpan(Span.Kind.CLIENT), parent);
    }
  }

  @Test public void customSampler() throws Exception {
    rpcTracing = RpcTracing.newBuilder(tracing).clientSampler(RpcRuleSampler.newBuilder()
      .putRule(methodEquals("sayGoodbye"), NEVER_SAMPLE)
      .putRule(serviceEquals("brave.dubbo"), ALWAYS_SAMPLE)
      .build()).build();
    init();

    // unsampled
    client.get().sayGoodbye("jorge");

    // sampled
    client.get().sayHello("jorge");

    assertThat(takeRemoteSpan(Span.Kind.CLIENT).name()).endsWith("sayhello");
    // @After will also check that sayGoodbye was not sampled
  }

  @Test public void reportsClientKindToZipkin() throws Exception {
    client.get().sayHello("jorge");

    takeRemoteSpan(Span.Kind.CLIENT);
  }

  @Test public void defaultSpanNameIsMethodName() throws Exception {
    client.get().sayHello("jorge");

    assertThat(takeRemoteSpan(Span.Kind.CLIENT).name())
      .isEqualTo("greeterservice/sayhello");
  }

  @Test public void onTransportException_addsErrorTag() throws Exception {
    server.stop();

    assertThatThrownBy(() -> client.get().sayHello("jorge"))
      .isInstanceOf(RpcException.class);

    takeRemoteSpanWithError(Span.Kind.CLIENT, ".*RemotingException.*");
  }

  @Test public void onTransportException_addsErrorTag_async() throws Exception {
    server.stop();

    RpcContext.getContext().asyncCall(() -> client.get().sayHello("romeo"));

    takeRemoteSpanWithError(Span.Kind.CLIENT, ".*RemotingException.*");
  }

  @Test public void flushesSpanOneWay() throws Exception {
    RpcContext.getContext().asyncCall(() -> {
      client.get().sayHello("romeo");
    });

    assertThat(takeFlushedSpan().kind())
      .isEqualTo(Span.Kind.CLIENT);
  }

  @Test public void addsErrorTag_onUnimplemented() throws Exception {
    assertThatThrownBy(() -> wrongClient.get().sayHello("jorge"))
      .isInstanceOf(RpcException.class);

    Span span = takeRemoteSpanWithError(Span.Kind.CLIENT, ".*Not found exported service.*");
    assertThat(span.tags().get("dubbo.error_code")).isEqualTo("1");
  }
}
