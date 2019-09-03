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
package brave.dubbo;

import brave.ScopedSpan;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.Sampler;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

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

    setTracing(tracingBuilder(Sampler.ALWAYS_SAMPLE).build());

    // perform a warmup request to allow CI to fail quicker
    client.get().sayHello("jorge");
    server.takeRequest();
    takeSpan();
  }

  @After public void stop() {
    if (wrongClient != null) wrongClient.destroy();
    super.stop();
  }

  @Test public void propagatesSpan() throws Exception {
    client.get().sayHello("jorge");

    TraceContext context = server.takeRequest().context();
    assertThat(context.parentId()).isNull();
    assertThat(context.sampled()).isTrue();

    takeSpan();
  }

  @Test public void makesChildOfCurrentSpan() throws Exception {
    ScopedSpan parent = tracing.tracer().startScopedSpan("test");
    try {
      client.get().sayHello("jorge");
    } finally {
      parent.finish();
    }

    TraceContext context = server.takeRequest().context();
    assertThat(context.traceId())
      .isEqualTo(parent.context().traceId());
    assertThat(context.parentId())
      .isEqualTo(parent.context().spanId());

    // we report one in-process and one RPC client span
    assertThat(Arrays.asList(takeSpan(), takeSpan()))
      .extracting(Span::kind)
      .containsOnly(null, Span.Kind.CLIENT);
  }

  /**
   * This tests that the parent is determined at the time the request was made, not when the request
   * was executed.
   */
  @Test public void usesParentFromInvocationTime() throws Exception {
    server.enqueueDelay(TimeUnit.SECONDS.toMillis(1));

    ScopedSpan parent = tracing.tracer().startScopedSpan("test");
    try {
      RpcContext.getContext().asyncCall(() -> client.get().sayHello("jorge"));
      RpcContext.getContext().asyncCall(() -> client.get().sayHello("romeo"));
    } finally {
      parent.finish();
    }

    ScopedSpan otherSpan = tracing.tracer().startScopedSpan("test2");
    try {
      for (int i = 0; i < 2; i++) {
        TraceContext context = server.takeRequest().context();
        assertThat(context.traceId())
          .isEqualTo(parent.context().traceId());
        assertThat(context.parentId())
          .isEqualTo(parent.context().spanId());
      }
    } finally {
      otherSpan.finish();
    }

    // Check we reported 2 in-process spans and 2 client spans
    assertThat(Arrays.asList(takeSpan(), takeSpan(), takeSpan(), takeSpan()))
      .extracting(Span::kind)
      .containsOnly(null, Span.Kind.CLIENT);
  }

  /** Unlike Brave 3, Brave 4 propagates trace ids even when unsampled */
  @Test public void propagates_sampledFalse() throws Exception {
    setTracing(tracingBuilder(Sampler.NEVER_SAMPLE).build());

    client.get().sayHello("jorge");
    TraceContextOrSamplingFlags extracted = server.takeRequest();
    assertThat(extracted.sampled()).isFalse();

    // @After will check that nothing is reported
  }

  @Test public void reportsClientKindToZipkin() throws Exception {
    client.get().sayHello("jorge");

    Span span = takeSpan();
    assertThat(span.kind())
      .isEqualTo(Span.Kind.CLIENT);
  }

  @Test public void defaultSpanNameIsMethodName() throws Exception {
    client.get().sayHello("jorge");

    Span span = takeSpan();
    assertThat(span.name())
      .isEqualTo("greeterservice/sayhello");
  }

  @Test public void onTransportException_addsErrorTag() throws Exception {
    server.stop();

    try {
      client.get().sayHello("jorge");
      failBecauseExceptionWasNotThrown(RpcException.class);
    } catch (RpcException e) {
    }

    Span span = takeSpan();
    assertThat(span.tags().get("error"))
      .contains("RemotingException");
  }

  @Test public void onTransportException_addsErrorTag_async() throws Exception {
    server.stop();

    RpcContext.getContext().asyncCall(() -> client.get().sayHello("romeo"));

    Span span = takeSpan();
    assertThat(span.tags().get("error"))
      .contains("RemotingException");
  }

  @Test public void flushesSpanOneWay() throws Exception {
    RpcContext.getContext().asyncCall(() -> {
      client.get().sayHello("romeo");
    });

    Span span = takeSpan();
    assertThat(span.duration())
      .isNull();
  }

  @Test public void addsErrorTag_onUnimplemented() throws Exception {
    assertThatThrownBy(() -> wrongClient.get().sayHello("jorge"))
      .isInstanceOf(RpcException.class);

    Span span = takeSpan();
    assertThat(span.tags().get("dubbo.error_code"))
      .isEqualTo("1");
    assertThat(span.tags().get("error"))
      .contains("Not found exported service");
  }
}
