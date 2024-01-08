/*
 * Copyright 2013-2023 The OpenZipkin Authors
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

import brave.Tag;
import brave.handler.MutableSpan;
import brave.propagation.B3SingleFormat;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.rpc.RpcResponseParser;
import brave.rpc.RpcRuleSampler;
import brave.rpc.RpcTracing;
import org.apache.dubbo.common.beanutil.JavaBeanDescriptor;
import org.apache.dubbo.common.beanutil.JavaBeanSerializeUtil;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static brave.Span.Kind.SERVER;
import static brave.rpc.RpcRequestMatchers.methodEquals;
import static brave.rpc.RpcRequestMatchers.serviceEquals;
import static brave.sampler.Sampler.ALWAYS_SAMPLE;
import static brave.sampler.Sampler.NEVER_SAMPLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ITTracingFilter_Provider extends ITTracingFilter {

  @BeforeEach void setup() {
    server.service.setFilter("tracing");
    server.service.setGeneric("true");
    server.service.setInterface(GreeterService.class);
    server.service.setRef((method, parameterTypes, args) -> {
      JavaBeanDescriptor arg =(JavaBeanDescriptor) args[0];

      if (arg.getProperty("value").equals("bad")) throw new IllegalArgumentException("bad");

      String value = currentTraceContext.get() != null
        ? currentTraceContext.get().traceIdString()
        : "";
      return JavaBeanSerializeUtil.serialize(value);
    });
    init();
    server.start();

    String url = "dubbo://" + server.ip() + ":" + server.port() + "?scope=remote&generic=bean";
    client = new ReferenceConfig<>();
    client.setGeneric("true");
    client.setInterface(GreeterService.class);
    client.setUrl(url);
    client.setRetries(-1);

    // perform a warmup request to allow CI to fail quicker
    client.get().sayHello("jorge");
    testSpanHandler.takeRemoteSpan(SERVER);
  }

  @Test void reusesPropagatedSpanId() {
    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);

    RpcContext.getClientAttachment().setAttachment("b3", B3SingleFormat.writeB3SingleFormat(parent));
    client.get().sayHello("jorge");

    assertSameIds(testSpanHandler.takeRemoteSpan(SERVER), parent);
  }

  @Test void createsChildWhenJoinDisabled() {
    tracing = tracingBuilder(NEVER_SAMPLE).supportsJoin(false).build();
    init();

    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);

    RpcContext.getClientAttachment().setAttachment("b3", B3SingleFormat.writeB3SingleFormat(parent));
    client.get().sayHello("jorge");

    MutableSpan span = testSpanHandler.takeRemoteSpan(SERVER);
    assertChildOf(span, parent);
    assertThat(span.id()).isNotEqualTo(parent.spanIdString());
  }

  @Test void samplingDisabled() {
    tracing = tracingBuilder(NEVER_SAMPLE).build();
    init();

    client.get().sayHello("jorge");

    // @After will check that nothing is reported
  }

  @Test void currentSpanVisibleToImpl() {
    assertThat(client.get().sayHello("jorge"))
        .isNotEmpty();

    testSpanHandler.takeRemoteSpan(SERVER);
  }

  @Test void reportsServerKindToZipkin() {
    client.get().sayHello("jorge");

    testSpanHandler.takeRemoteSpan(SERVER);
  }

  @Test void defaultSpanNameIsMethodName() {
    client.get().sayHello("jorge");

    assertThat(testSpanHandler.takeRemoteSpan(SERVER).name())
        .isEqualTo("brave.dubbo.GreeterService/sayHello");
  }

  @Test void setsErrorOnException() {
    assertThatThrownBy(() -> client.get().sayHello("bad"))
        .isInstanceOf(IllegalArgumentException.class);

    testSpanHandler.takeRemoteSpanWithErrorMessage(SERVER, "bad");
  }

  /* RpcTracing-specific feature tests */

  @Test void customSampler() {
    RpcTracing rpcTracing = RpcTracing.newBuilder(tracing).serverSampler(RpcRuleSampler.newBuilder()
        .putRule(methodEquals("sayGoodbye"), NEVER_SAMPLE)
        .putRule(serviceEquals("brave.dubbo"), ALWAYS_SAMPLE)
        .build()).build();
    init().setRpcTracing(rpcTracing);

    // unsampled
    client.get().sayGoodbye("jorge");

    // sampled
    client.get().sayHello("jorge");

    assertThat(testSpanHandler.takeRemoteSpan(SERVER).name()).endsWith("sayHello");
    // @After will also check that sayGoodbye was not sampled
  }

  @Test void customParser() {
    Tag<DubboResponse> javaValue = new Tag<DubboResponse>("dubbo.result_value") {
      @Override protected String parseValue(DubboResponse input, TraceContext context) {
        Result result = input.result();
        if (result == null) return null;
        Object value = ((JavaBeanDescriptor) result.getValue()).getProperty("value");
        return String.valueOf(value);
      }
    };

    RpcTracing rpcTracing = RpcTracing.newBuilder(tracing)
        .serverResponseParser((res, context, span) -> {
          RpcResponseParser.DEFAULT.parse(res, context, span);
          if (res instanceof DubboResponse) {
            javaValue.tag((DubboResponse) res, span);
          }
        }).build();
    init().setRpcTracing(rpcTracing);

    String javaResult = client.get().sayHello("jorge");

    Map<String, String> tags = testSpanHandler.takeRemoteSpan(SERVER).tags();
    assertThat(tags).containsEntry("dubbo.result_value", javaResult);
  }
}
