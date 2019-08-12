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

import brave.Tracing;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.config.ReferenceConfig;
import com.alibaba.dubbo.rpc.Filter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ITTracingFilter {
  /** See brave.http.ITHttp for rationale on using a concurrent blocking queue */
  BlockingQueue<Span> spans = new LinkedBlockingQueue<>();

  Tracing tracing;
  TestServer server = new TestServer();
  ReferenceConfig<GreeterService> client;

  @After public void stop() {
    if (client != null) client.destroy();
    server.stop();
    Tracing current = Tracing.current();
    if (current != null) current.close();
  }

  // See brave.http.ITHttp for rationale on polling after tests complete
  @Rule public TestRule assertSpansEmpty = new TestWatcher() {
    // only check success path to avoid masking assertion errors or exceptions
    @Override protected void succeeded(Description description) {
      try {
        assertThat(spans.poll(100, TimeUnit.MILLISECONDS))
          .withFailMessage("Span remaining in queue. Check for redundant reporting")
          .isNull();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  };

  Tracing.Builder tracingBuilder(Sampler sampler) {
    return Tracing.newBuilder()
      .spanReporter(spans::add)
      .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
        .addScopeDecorator(StrictScopeDecorator.create())
        .build())
      .sampler(sampler);
  }

  void setTracing(Tracing tracing) {
    ((TracingFilter) ExtensionLoader.getExtensionLoader(Filter.class)
      .getExtension("tracing"))
      .setTracing(tracing);
    this.tracing = tracing;
  }

  /** Call this to block until a span was reported */
  Span takeSpan() throws InterruptedException {
    Span result = spans.poll(3, TimeUnit.SECONDS);
    assertThat(result)
      .withFailMessage("Span was not reported")
      .isNotNull();
    return result;
  }
}
