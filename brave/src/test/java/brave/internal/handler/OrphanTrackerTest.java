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
package brave.internal.handler;

import brave.TracerTest;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import static org.assertj.core.api.Assertions.assertThat;

public class OrphanTrackerTest {
  @Rule public TestName testName = new TestName();

  List<String> messages = new ArrayList<>();
  List<Throwable> throwables = new ArrayList<>();
  AtomicInteger clock = new AtomicInteger(1);
  TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
  MutableSpan defaultSpan, span;
  OrphanTracker tracker;

  @Before public void setup() {
    defaultSpan = new MutableSpan();
    defaultSpan.localServiceName(TracerTest.class.getSimpleName());
    defaultSpan.localIp("127.0.0.1");
    span = new MutableSpan(context, defaultSpan); // same as how PendingSpans initializes

    tracker = orphanTrackerWithFakeLogger();
  }

  @Test public void allocatedAndUsed_logsAndAddsFlushAnnotation() {
    span.startTimestamp(1L);
    assertThat(tracker.begin(context, span, null)).isTrue();
    assertThat(tracker.end(context, span, SpanHandler.Cause.ORPHANED)).isTrue();

    assertThat(span.annotationTimestampAt(0)).isOne(); // initial value of clock
    assertThat(span.annotationValueAt(0)).isEqualTo("brave.flush");

    assertThat(messages).containsExactly(
        "Span 0000000000000001/0000000000000002 neither finished nor flushed before GC"
    );
    assertThrowableIdentifiesCaller();
  }

  /** This prevents overhead on dropped spans */
  @Test public void allocatedButNotUsed_logsButDoesntAddFlushAnnotation() {
    assertThat(tracker.begin(context, span, null)).isTrue();
    // We return true to let metrics handlers work
    assertThat(tracker.end(context, span, SpanHandler.Cause.ORPHANED)).isTrue();

    assertThat(span.annotationCount()).isZero();

    assertThat(messages).containsExactly(
        "Span 0000000000000001/0000000000000002 was allocated but never used"
    );
    assertThrowableIdentifiesCaller();
  }

  void assertThrowableIdentifiesCaller() {
    assertThat(throwables).hasSize(1);
    assertThat(throwables.get(0))
        .hasNoCause()
        .hasMessage("Thread main allocated span here")
        .hasStackTraceContaining(testName.getMethodName());
  }

  OrphanTracker orphanTrackerWithFakeLogger() {
    Logger logger = new Logger("", null) {
      {
        setLevel(Level.ALL);
      }

      @Override public void log(Level level, String msg, Throwable throwable) {
        assertThat(level).isEqualTo(Level.FINE);
        messages.add(msg);
        throwables.add(throwable);
      }
    };

    return new OrphanTracker(OrphanTracker.newBuilder()
        .clock(clock::getAndIncrement).defaultSpan(defaultSpan)) {
      @Override Logger logger() {
        return logger;
      }
    };
  }
}
