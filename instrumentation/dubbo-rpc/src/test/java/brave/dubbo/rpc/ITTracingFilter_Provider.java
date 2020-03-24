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

import brave.propagation.B3SingleFormat;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.rpc.RpcRuleSampler;
import brave.rpc.RpcTracing;
import com.alibaba.dubbo.common.beanutil.JavaBeanDescriptor;
import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ReferenceConfig;
import com.alibaba.dubbo.rpc.RpcContext;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Span;

import static brave.rpc.RpcRequestMatchers.methodEquals;
import static brave.rpc.RpcRequestMatchers.serviceEquals;
import static brave.sampler.Sampler.ALWAYS_SAMPLE;
import static brave.sampler.Sampler.NEVER_SAMPLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ITTracingFilter_Provider extends ITTracingFilter {

  @Before public void setup() {
    server.service.setFilter("tracing");
    server.service.setInterface(GreeterService.class);
    server.service.setRef((method, parameterTypes, args) -> {
      JavaBeanDescriptor arg = (JavaBeanDescriptor) args[0];
      if (arg.getProperty("value").equals("bad")) {
        throw new IllegalArgumentException();
      }
      String value = currentTraceContext.get() != null
        ? currentTraceContext.get().traceIdString()
        : "";
      arg.setProperty("value", value);
      return args[0];
    });
    server.start();

    ReferenceConfig<GreeterService> ref = new ReferenceConfig<>();
    ref.setApplication(new ApplicationConfig("bean-consumer"));
    ref.setInterface(GreeterService.class);
    ref.setUrl("dubbo://" + server.ip() + ":" + server.port() + "?scope=remote&generic=bean");
    client = ref;

    init();
  }

  @Test public void reusesPropagatedSpanId() throws Exception {
    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);

    RpcContext.getContext().getAttachments().put("b3", B3SingleFormat.writeB3SingleFormat(parent));
    client.get().sayHello("jorge");

    assertSameIds(takeRemoteSpan(Span.Kind.SERVER), parent);
  }

  @Test public void createsChildWhenJoinDisabled() throws Exception {
    tracing = tracingBuilder(NEVER_SAMPLE).supportsJoin(false).build();
    rpcTracing = RpcTracing.create(tracing);
    init();

    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);

    RpcContext.getContext().getAttachments().put("b3", B3SingleFormat.writeB3SingleFormat(parent));
    client.get().sayHello("jorge");

    assertChildOf(takeRemoteSpan(Span.Kind.SERVER), parent);
  }

  @Test public void samplingDisabled() {
    tracing = tracingBuilder(NEVER_SAMPLE).build();
    rpcTracing = RpcTracing.create(tracing);
    init();

    client.get().sayHello("jorge");

    // @After will check that nothing is reported
  }

  @Test public void currentSpanVisibleToImpl() throws Exception {
    assertThat(client.get().sayHello("jorge"))
      .isNotEmpty();

    takeRemoteSpan(Span.Kind.SERVER);
  }

  @Test public void reportsServerKindToZipkin() throws Exception {
    client.get().sayHello("jorge");

    assertThat(takeRemoteSpan(Span.Kind.SERVER).kind())
      .isEqualTo(Span.Kind.SERVER);
  }

  @Test public void defaultSpanNameIsMethodName() throws Exception {
    client.get().sayHello("jorge");

    assertThat(takeRemoteSpan(Span.Kind.SERVER).name())
      .isEqualTo("genericservice/sayhello");
  }

  @Test public void addsErrorTagOnException() throws Exception {
    assertThatThrownBy(() -> client.get().sayHello("bad"))
      .isInstanceOf(IllegalArgumentException.class);

    takeRemoteSpanWithError(Span.Kind.SERVER, "IllegalArgumentException");
  }

  @Test public void customSampler() throws Exception {
    rpcTracing = RpcTracing.newBuilder(tracing).serverSampler(RpcRuleSampler.newBuilder()
      .putRule(methodEquals("sayGoodbye"), NEVER_SAMPLE)
      .putRule(serviceEquals("brave.dubbo"), ALWAYS_SAMPLE)
      .build()).build();
    init();

    // unsampled
    client.get().sayGoodbye("jorge");

    // sampled
    client.get().sayHello("jorge");

    assertThat(takeRemoteSpan(Span.Kind.SERVER).name()).endsWith("sayhello");
    // @After will also check that sayGoodbye was not sampled
  }
}
