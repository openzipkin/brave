/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.internal.handler;

import brave.TracerTest;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static org.assertj.core.api.Assertions.assertThat;

class OrphanTrackerTest {
  String testName;
  List<String> messages = new ArrayList<>();
  List<Throwable> throwables = new ArrayList<>();
  AtomicInteger clock = new AtomicInteger(1);
  TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
  MutableSpan defaultSpan, span;
  OrphanTracker tracker;

  @BeforeEach void setup(TestInfo testInfo) {
    Optional<Method> testMethod = testInfo.getTestMethod();
    if (testMethod.isPresent()) {
      this.testName = testMethod.get().getName();
    }
    defaultSpan = new MutableSpan();
    defaultSpan.localServiceName(TracerTest.class.getSimpleName());
    defaultSpan.localIp("127.0.0.1");
    span = new MutableSpan(context, defaultSpan); // same as how PendingSpans initializes

    tracker = orphanTrackerWithFakeLogger();
  }

  @Test void allocatedAndUsed_logsAndAddsFlushAnnotation() {
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
  @Test void allocatedButNotUsed_logsButDoesntAddFlushAnnotation() {
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
      .hasStackTraceContaining(testName);
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
