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
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.rpc.RpcRuleSampler;
import brave.rpc.RpcTracing;
import brave.test.util.AssertableCallback;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
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

  @Before public void setup() {
    server.start();

    String url = "dubbo://" + server.ip() + ":" + server.port() + "?scope=remote&generic=bean";
    client = new ReferenceConfig<>();
    client.setGeneric("true");
    client.setFilter("tracing");
    client.setInterface(GreeterService.class);
    client.setUrl(url);

    wrongClient = new ReferenceConfig<>();
    wrongClient.setGeneric("true");
    wrongClient.setFilter("tracing");
    wrongClient.setInterface(GraterService.class);
    wrongClient.setUrl(url);

    DubboBootstrap.getInstance().application(application)
      .reference(client)
      .reference(wrongClient)
      .start();

    init();

    // perform a warmup request to allow CI to fail quicker
    client.get().sayHello("jorge");
    server.takeRequest();
    reporter.takeRemoteSpan(Span.Kind.CLIENT);
  }

  @After public void stop() {
    if (wrongClient != null) wrongClient.destroy();
    super.stop();
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
    AssertableCallback<String> items1 = new AssertableCallback<>();
    AssertableCallback<String> items2 = new AssertableCallback<>();

    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      RpcContext.getContext().asyncCall(() -> client.get().sayHello("jorge"))
        .whenComplete(items1);
      RpcContext.getContext().asyncCall(() -> client.get().sayHello("romeo"))
        .whenComplete(items2);
    }

    try (Scope scope = currentTraceContext.newScope(null)) {
      // complete within a different scope
      items1.join();
      items2.join();

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

  @Test public void customSampler() {
    rpcTracing = RpcTracing.newBuilder(tracing).clientSampler(RpcRuleSampler.newBuilder()
      .putRule(methodEquals("sayGoodbye"), NEVER_SAMPLE)
      .putRule(serviceEquals("brave.dubbo"), ALWAYS_SAMPLE)
      .build()).build();
    init();

    // unsampled
    client.get().sayGoodbye("jorge");

    // sampled
    client.get().sayHello("jorge");

    assertThat(reporter.takeRemoteSpan(Span.Kind.CLIENT).name()).endsWith("sayhello");
    // @After will also check that sayGoodbye was not sampled
  }

  @Test public void reportsClientKindToZipkin() {
    client.get().sayHello("jorge");

    reporter.takeRemoteSpan(Span.Kind.CLIENT);
  }

  @Test public void defaultSpanNameIsMethodName() {
    client.get().sayHello("jorge");

    assertThat(reporter.takeRemoteSpan(Span.Kind.CLIENT).name())
      .isEqualTo("brave.dubbo.greeterservice/sayhello");
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

  @Test public void finishesOneWaySpan() {
    RpcContext.getContext().asyncCall(() -> {
      client.get().sayHello("romeo");
    });

    reporter.takeRemoteSpan(Span.Kind.CLIENT);
  }

  @Test public void addsErrorTag_onUnimplemented() {
    assertThatThrownBy(() -> wrongClient.get().sayHello("jorge"))
      .isInstanceOf(RpcException.class);

    Span span =
      reporter.takeRemoteSpanWithError(Span.Kind.CLIENT, ".*Not found exported service.*");
    assertThat(span.tags().get("dubbo.error_code")).isEqualTo("1");
  }
}
