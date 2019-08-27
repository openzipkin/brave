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
package brave;

import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.propagation.B3SinglePropagation;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Test;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TracingTest {
  List<zipkin2.Span> spans = new ArrayList<>();
  List<MutableSpan> mutableSpans = new ArrayList<>();
  FinishedSpanHandler finishedSpanHandler = new FinishedSpanHandler() {
    @Override public boolean handle(TraceContext context, MutableSpan span) {
      mutableSpans.add(span);
      return true;
    }
  };

  /**
   * This behavior could be problematic as downstream services may report spans based on propagated
   * sampled status, and be missing a parent when their parent tracer is in noop.
   */
  @Test public void setNoop_dropsDataButDoesntAffectSampling() {
    try (Tracing tracing = Tracing.newBuilder().spanReporter(spans::add).build()) {
      ScopedSpan parent = tracing.tracer().startScopedSpan("parent");

      tracing.setNoop(true);

      // a new child retains sampled from parent even in noop
      brave.Span child = tracing.tracer().newChild(parent.context());
      assertThat(child.context().sampled()).isTrue();
      assertThat(child.isNoop()).isTrue();
      child.finish();

      parent.finish();

      // a new trace is sampled from even when noop
      brave.Span root = tracing.tracer().newTrace();
      assertThat(root.context().sampled()).isTrue();
      assertThat(root.isNoop()).isTrue();
      root.finish();
    }

    assertThat(spans).isEmpty();
  }

  @Test public void spanReporter_getsLocalEndpointInfo() {
    String expectedLocalServiceName = "favistar", expectedLocalIp = "1.2.3.4";
    int expectedLocalPort = 80;

    List<Span> zipkinSpans = new ArrayList<>();
    Reporter<Span> spanReporter = span -> {
      assertThat(span.localServiceName()).isEqualTo(expectedLocalServiceName);
      assertThat(span.localEndpoint().ipv4()).isEqualTo(expectedLocalIp);
      assertThat(span.localEndpoint().portAsInt()).isEqualTo(expectedLocalPort);
      zipkinSpans.add(span);
    };

    try (Tracing tracing = Tracing.newBuilder()
      .localServiceName(expectedLocalServiceName)
      .localIp(expectedLocalIp)
      .localPort(expectedLocalPort)
      .spanReporter(spanReporter)
      .build()) {
      tracing.tracer().newTrace().start().finish();
    }

    assertThat(zipkinSpans).isNotEmpty(); // ensures the assertions passed.
  }

  @Test public void finishedSpanHandler_zipkinByDefault() {
    try (Tracing tracing = Tracing.newBuilder().build()) {
      assertThat(tracing.tracer().finishedSpanHandler).extracting("delegate.spanReporter")
        .isInstanceOf(Tracing.LoggingReporter.class);
    }
  }

  @Test public void finishedSpanHandler_ignoresNoop() {
    try (Tracing tracing = Tracing.newBuilder()
      .addFinishedSpanHandler(FinishedSpanHandler.NOOP)
      .build()) {
      assertThat(tracing.tracer().finishedSpanHandler).extracting("delegate.spanReporter")
        .isInstanceOf(Tracing.LoggingReporter.class);
    }
  }

  @Test public void finishedSpanHandler_multiple() {
    FinishedSpanHandler one = new FinishedSpanHandler() {
      @Override public boolean handle(TraceContext context, MutableSpan span) {
        return true;
      }
    };
    FinishedSpanHandler two = new FinishedSpanHandler() {
      @Override public boolean handle(TraceContext context, MutableSpan span) {
        return true;
      }
    };
    try (Tracing tracing = Tracing.newBuilder()
      .addFinishedSpanHandler(one)
      .addFinishedSpanHandler(two)
      .build()) {
      assertThat(tracing.tracer().finishedSpanHandler).extracting("handlers")
        .satisfies(handlers -> assertThat((FinishedSpanHandler[]) handlers)
          .startsWith(one, two)
          .hasSize(3) // zipkin and the above
        );
    }
  }

  /** This test lets future maintainers know that we don't dedupe, which can explain future bugs. */
  @Test public void finishedSpanHandler_doesntDedupe() {
    FinishedSpanHandler finishedSpanHandler = new FinishedSpanHandler() {
      @Override public boolean handle(TraceContext context, MutableSpan span) {
        return true;
      }
    };

    try (Tracing tracing = Tracing.newBuilder()
      .addFinishedSpanHandler(finishedSpanHandler)
      .addFinishedSpanHandler(finishedSpanHandler)
      .build()) {
      assertThat(tracing.tracer().finishedSpanHandler).extracting("handlers")
        .satisfies(handlers -> assertThat((FinishedSpanHandler[]) handlers)
          .startsWith(finishedSpanHandler, finishedSpanHandler)
          .hasSize(3) // zipkin and the above
        );
    }
  }

  @Test public void finishedSpanHandler_dataChangesVisibleToZipkin() {
    String serviceNameOverride = "favistar";

    FinishedSpanHandler finishedSpanHandler = new FinishedSpanHandler() {
      @Override public boolean handle(TraceContext context, MutableSpan span) {
        span.localServiceName(serviceNameOverride);
        return true;
      }
    };

    try (Tracing tracing = Tracing.newBuilder()
      .spanReporter(spans::add)
      .addFinishedSpanHandler(finishedSpanHandler)
      .build()) {
      tracing.tracer().newTrace().start().finish();
    }

    assertThat(spans.get(0).localServiceName()).isEqualTo(serviceNameOverride);
  }

  @Test public void finishedSpanHandler_recordsWhenSampled() {
    try (Tracing tracing = Tracing.newBuilder()
      .spanReporter(spans::add)
      .addFinishedSpanHandler(finishedSpanHandler)
      .build()) {
      tracing.tracer().newTrace().start().name("aloha").finish();
    }

    assertThat(spans).hasSameSizeAs(mutableSpans).hasSize(1);
    assertThat(spans.get(0).name()).isEqualTo(mutableSpans.get(0).name());
    assertThat(spans.get(0).timestampAsLong()).isEqualTo(mutableSpans.get(0).startTimestamp());
    long mutableSpanDuration =
      Math.max(1, mutableSpans.get(0).finishTimestamp() - mutableSpans.get(0).startTimestamp());
    assertThat(spans.get(0).durationAsLong()).isEqualTo(mutableSpanDuration);
  }

  @Test public void finishedSpanHandler_doesntRecordWhenUnsampled() {
    try (Tracing tracing = Tracing.newBuilder()
      .spanReporter(spans::add)
      .addFinishedSpanHandler(finishedSpanHandler)
      .sampler(Sampler.NEVER_SAMPLE)
      .build()) {
      tracing.tracer().newTrace().start().name("aloha").finish();
    }

    assertThat(spans).isEmpty();
    assertThat(mutableSpans).isEmpty();
  }

  @Test public void finishedSpanHandler_recordsWhenReporterIsNoopIfAlwaysSampleLocal() {
    try (Tracing tracing = Tracing.newBuilder()
      .spanReporter(Reporter.NOOP)
      .addFinishedSpanHandler(finishedSpanHandler)
      .build()) {
      tracing.tracer().newTrace().start().name("aloha").finish();
    }

    assertThat(spans).isEmpty();
    assertThat(mutableSpans).hasSize(1);
  }

  @Test public void finishedSpanHandler_recordsWhenUnsampledIfAlwaysSampleLocal() {
    try (Tracing tracing = Tracing.newBuilder()
      .spanReporter(spans::add)
      .addFinishedSpanHandler(new FinishedSpanHandler() {
        @Override public boolean handle(TraceContext context, MutableSpan span) {
          mutableSpans.add(span);
          return true;
        }

        @Override public boolean alwaysSampleLocal() {
          return true;
        }
      })
      .sampler(Sampler.NEVER_SAMPLE)
      .build()) {
      tracing.tracer().newTrace().start().name("aloha").finish();
    }

    assertThat(spans).isEmpty();
    assertThat(mutableSpans).hasSize(1);
  }

  @Test public void finishedSpanHandler_recordsWhenUnsampledIfContextSamplesLocal() {
    AtomicBoolean sampledLocal = new AtomicBoolean();
    try (Tracing tracing = Tracing.newBuilder()
      .spanReporter(spans::add)
      .propagationFactory(new Propagation.Factory() {
        @Override public <K> Propagation<K> create(Propagation.KeyFactory<K> keyFactory) {
          return B3SinglePropagation.FACTORY.create(keyFactory);
        }

        @Override public TraceContext decorate(TraceContext context) {
          if (sampledLocal.getAndSet(true)) return context;
          return context.toBuilder().sampledLocal(true).build();
        }
      })
      .addFinishedSpanHandler(finishedSpanHandler)
      .sampler(Sampler.NEVER_SAMPLE)
      .build()) {
      tracing.tracer().newTrace().start().name("one").finish();
      tracing.tracer().newTrace().start().name("two").finish();
    }

    assertThat(spans).isEmpty();
    assertThat(mutableSpans).hasSize(1);
    assertThat(mutableSpans.get(0).name()).isEqualTo("one");
  }

  /** This ensures handlers not designed for orphans aren't accidentally used. */
  @Test public void finishedSpanHandler_splitsHandlersBasedOnOrphanSupport() {
    FinishedSpanHandler one = mock(FinishedSpanHandler.class);
    when(one.supportsOrphans()).thenReturn(true);
    FinishedSpanHandler two = mock(FinishedSpanHandler.class);
    when(two.supportsOrphans()).thenReturn(false);
    FinishedSpanHandler three = mock(FinishedSpanHandler.class);
    when(three.supportsOrphans()).thenReturn(true);
    FinishedSpanHandler four = mock(FinishedSpanHandler.class);
    when(four.supportsOrphans()).thenReturn(false);

    try (Tracing tracing = Tracing.newBuilder()
      .addFinishedSpanHandler(one)
      .addFinishedSpanHandler(two)
      .addFinishedSpanHandler(three)
      .addFinishedSpanHandler(four)
      .build()) {

      // Handlers, regardless of whether they support orphans, should be invoked in order
      assertThat(tracing.tracer().finishedSpanHandler).extracting("handlers")
        .asInstanceOf(InstanceOfAssertFactories.ARRAY)
        .startsWith(one, two, three, four);

      // Only orphaned handlers should be invoked on the orphan path
      assertThat(tracing.tracer().pendingSpans).extracting("orphanedSpanHandler.handlers")
        .asInstanceOf(InstanceOfAssertFactories.ARRAY)
        .startsWith(one, three);
    }
  }
}
