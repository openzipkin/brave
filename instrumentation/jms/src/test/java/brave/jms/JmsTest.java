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

import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.jms.JMSException;
import javax.jms.Message;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class JmsTest {
  @After public void tearDown() {
    tracing.close();
  }

  /**
   * See brave.http.ITHttp for rationale on using a concurrent blocking queue eventhough some calls,
   * like those using blocking clients, happen on the main thread.
   */
  BlockingQueue<Span> spans = new LinkedBlockingQueue<>();

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

  /** Call this to block until a span was reported */
  Span takeSpan() throws InterruptedException {
    Span result = spans.poll(3, TimeUnit.SECONDS);
    assertThat(result)
      .withFailMessage("Span was not reported")
      .isNotNull();
    return result;
  }

  Tracing tracing = Tracing.newBuilder()
    .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
      .addScopeDecorator(StrictScopeDecorator.create())
      .build())
    .spanReporter(spans::add)
    .build();
  CurrentTraceContext current = tracing.currentTraceContext();
  JmsTracing jmsTracing = JmsTracing.create(tracing);

  static Map<String, String> propertiesToMap(Message headers) throws Exception {
    Map<String, String> result = new LinkedHashMap<>();

    Enumeration<String> names = headers.getPropertyNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      result.put(name, headers.getStringProperty(name));
    }
    return result;
  }

  interface JMSRunnable {
    void run() throws JMSException;
  }
}
