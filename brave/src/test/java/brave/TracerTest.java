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
package brave;

import brave.Tracer.SpanInScope;
import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.internal.Platform;
import brave.internal.handler.OrphanTracker;
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.Propagation;
import brave.propagation.SamplingFlags;
import brave.propagation.StrictCurrentTraceContext;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.propagation.TraceIdContext;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunctions;
import brave.test.TestSpanHandler;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.After;
import org.junit.Test;
import zipkin2.Endpoint;
import zipkin2.reporter.Reporter;

import static brave.Span.Kind.CLIENT;
import static brave.Span.Kind.SERVER;
import static brave.propagation.SamplingFlags.EMPTY;
import static brave.sampler.SamplerFunctions.deferDecision;
import static brave.sampler.SamplerFunctions.neverSample;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class TracerTest {
  static final BaggageField BAGGAGE_FIELD = BaggageField.create("user-id");

  TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).shared(true).build();
  TestSpanHandler spans = new TestSpanHandler();
  Propagation.Factory propagationFactory = B3Propagation.FACTORY;
  CurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create();
  Propagation.Factory baggageFactory = BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
    .add(SingleBaggageField.remote(BAGGAGE_FIELD)).build();
  Tracer tracer = Tracing.newBuilder()
    .addSpanHandler(spans)
    .trackOrphans()
    .propagationFactory(new Propagation.Factory() {
      @Deprecated @Override public <K> Propagation<K> create(Propagation.KeyFactory<K> keyFactory) {
        return propagationFactory.create(keyFactory);
      }

      @Override public boolean supportsJoin() {
        return propagationFactory.supportsJoin();
      }

      @Override public boolean requires128BitTraceId() {
        return propagationFactory.requires128BitTraceId();
      }

      @Override public TraceContext decorate(TraceContext context) {
        return propagationFactory.decorate(context);
      }
    })
    .currentTraceContext(currentTraceContext)
    .localServiceName("my-service")
    .build().tracer();

  @After public void close() {
    Tracing.current().close();
  }

  @Test public void handler_hasNiceToString() {
    tracer = Tracing.newBuilder().build().tracer();

    assertThat((Object) tracer.pendingSpans).extracting("spanHandler")
      .hasToString("LogSpanHandler{name=brave.Tracer}");
  }

  @Test public void sampler() {
    Sampler sampler = new Sampler() {
      @Override public boolean isSampled(long traceId) {
        return false;
      }
    };

    tracer = Tracing.newBuilder().sampler(sampler).build().tracer();

    assertThat(tracer.sampler)
      .isSameAs(sampler);
  }

  @Test public void withSampler() {
    Sampler sampler = new Sampler() {
      @Override public boolean isSampled(long traceId) {
        return false;
      }
    };

    tracer = tracer.withSampler(sampler);

    assertThat(tracer.sampler)
      .isSameAs(sampler);
  }

  @Test public void localServiceName() {
    tracer = Tracing.newBuilder().localServiceName("my-foo").build().tracer();

    assertThat(tracer).extracting("pendingSpans.defaultSpan.localServiceName")
      .isEqualTo("my-foo");
  }

  @Test public void localServiceName_defaultIsUnknown() {
    tracer = Tracing.newBuilder().build().tracer();

    assertThat(tracer).extracting("pendingSpans.defaultSpan.localServiceName")
      .isEqualTo("unknown");
  }

  @Test public void localServiceName_ignoredWhenGivenLocalEndpoint() {
    Endpoint endpoint = Endpoint.newBuilder().ip("1.2.3.4").serviceName("my-bar").build();
    tracer = Tracing.newBuilder().localServiceName("my-foo").endpoint(endpoint).build().tracer();

    MutableSpan defaultSpan = new MutableSpan();
    defaultSpan.localServiceName("my-bar");
    defaultSpan.localIp("1.2.3.4");
    assertThat(tracer).extracting("pendingSpans.defaultSpan").isEqualTo(defaultSpan);
  }

  @Test public void newTrace_isRootSpan() {
    assertThat(tracer.newTrace())
      .satisfies(s -> assertThat(s.context().parentId()).isNull())
      .isInstanceOf(RealSpan.class);
  }

  @Test public void newTrace_traceId128Bit() {
    tracer = Tracing.newBuilder().traceId128Bit(true).build().tracer();

    assertThat(tracer.newTrace().context().traceIdHigh())
      .isNotZero();
  }

  @Test public void newTrace_notSampled_tracer() {
    tracer = tracer.withSampler(Sampler.NEVER_SAMPLE);

    assertThat(tracer.newTrace())
      .isInstanceOf(NoopSpan.class);
  }

  /** When we join a sampled request, we are sharing the same trace identifiers. */
  @Test public void join_setsShared() {
    TraceContext fromIncomingRequest = tracer.newTrace().context();
    assertThat(fromIncomingRequest.shared()).isFalse();
    assertThat(fromIncomingRequest.isLocalRoot()).isTrue();

    TraceContext joined = tracer.joinSpan(fromIncomingRequest).context();
    assertThat(joined.shared()).isTrue();
    assertThat(joined.isLocalRoot()).isFalse();
  }

  /**
   * Data from loopback requests should be partitioned into two spans: one for the client and the
   * other for the server.
   */
  @Test public void join_sharedDataIsSeparate() {
    Span clientSide = tracer.newTrace().kind(CLIENT).start(1L);
    Span serverSide = tracer.joinSpan(clientSide.context()).kind(SERVER).start(2L);
    serverSide.finish(3L);
    clientSide.finish(4L);

    // Ensure they use the same span ID (sanity check)
    String spanId = spans.get(0).id();
    assertThat(spans).extracting(MutableSpan::id)
      .containsExactly(spanId, spanId);

    // Ensure the important parts are separated correctly
    assertThat(spans).extracting(
      MutableSpan::kind, MutableSpan::shared, MutableSpan::startTimestamp, MutableSpan::finishTimestamp
    ).containsExactly(
      tuple(SERVER, true, 2L, 3L),
      tuple(CLIENT, false, 1L, 4L)
    );
  }

  @Test public void join_createsChildWhenUnsupported() {
    tracer = Tracing.newBuilder().supportsJoin(false).addSpanHandler(spans).build().tracer();

    TraceContext fromIncomingRequest = tracer.newTrace().context();

    TraceContext shouldBeChild = tracer.joinSpan(fromIncomingRequest).context();
    assertThat(shouldBeChild.shared())
      .isFalse();
    assertThat(shouldBeChild.parentId())
      .isEqualTo(fromIncomingRequest.spanId());
  }

  @Test public void finish_doesntCrashOnBadReporter() {
    tracer = Tracing.newBuilder().addSpanHandler(new SpanHandler() {
      @Override public boolean begin(TraceContext context, MutableSpan span, TraceContext parent) {
        throw new RuntimeException();
      }
    }).build().tracer();

    tracer.newTrace().start().finish();
  }

  @Test public void join_createsChildWhenUnsupportedByPropagation() {
    tracer = Tracing.newBuilder()
      .propagationFactory(new Propagation.Factory() {
        @Deprecated public <K> Propagation<K> create(Propagation.KeyFactory<K> keyFactory) {
          return B3Propagation.FACTORY.create(keyFactory);
        }
      })
      .addSpanHandler(spans).build().tracer();

    TraceContext fromIncomingRequest = tracer.newTrace().context();

    TraceContext shouldBeChild = tracer.joinSpan(fromIncomingRequest).context();
    assertThat(shouldBeChild.shared())
      .isFalse();
    assertThat(shouldBeChild.parentId())
      .isEqualTo(fromIncomingRequest.spanId());
  }

  @Test public void join_noop() {
    TraceContext fromIncomingRequest = tracer.newTrace().context();

    tracer.noop.set(true);

    assertThat(tracer.joinSpan(fromIncomingRequest))
      .isInstanceOf(NoopSpan.class);
  }

  @Test public void join_ensuresSampling() {
    TraceContext notYetSampled =
      tracer.newTrace().context().toBuilder().sampled(null).build();

    assertThat(tracer.joinSpan(notYetSampled).context())
      .isEqualTo(notYetSampled.toBuilder().sampled(true).build());
  }

  @Test public void newChild_ensuresSampling() {
    TraceContext notYetSampled =
      tracer.newTrace().context().toBuilder().sampled(null).build();

    assertThat(tracer.newChild(notYetSampled).context().sampled())
      .isTrue();
  }

  @Test public void nextSpan_ensuresSampling_whenCreatingNewChild() {
    TraceContext notYetSampled =
      tracer.newTrace().context().toBuilder().sampled(null).build();

    TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.create(notYetSampled);
    assertThat(tracer.nextSpan(extracted).context().sampled())
      .isTrue();
  }

  @Test public void toSpan() {
    TraceContext context = tracer.newTrace().context();

    assertThat(tracer.toSpan(context))
      .isInstanceOf(RealSpan.class)
      .extracting(Span::context)
      .isEqualTo(context);
  }

  @Test public void toSpan_decoratesExternalContext() {
    assertThat(tracer.toSpan(context).context())
      .isNotSameAs(context)
      .extracting(TraceContext::localRootId)
      .isEqualTo(context.spanId());
  }

  @Test public void toSpan_doesntRedundantlyDecorateContext() {
    TraceContext context = tracer.newTrace().context();

    assertThat(tracer.toSpan(context).context())
      .isSameAs(context);
  }

  @Test public void toSpan_noop() {
    TraceContext context = tracer.newTrace().context();

    tracer.noop.set(true);

    assertThat(tracer.toSpan(context))
      .isInstanceOf(NoopSpan.class);
  }

  @Test public void toSpan_sampledLocalIsNotNoop() {
    TraceContext sampledLocal = tracer.newTrace().context()
      .toBuilder().sampled(false).sampledLocal(true).build();

    assertThat(tracer.toSpan(sampledLocal))
      .isInstanceOf(RealSpan.class);
  }

  @Test public void toSpan_notSampledIsNoop() {
    TraceContext notSampled =
      tracer.nextSpanWithParent(SamplerFunctions.neverSample(), false, null).context();

    assertThat(tracer.toSpan(notSampled).isNoop()).isTrue();
  }

  @Test public void newChild() {
    TraceContext parent = tracer.newTrace().context();

    assertThat(tracer.newChild(parent))
      .satisfies(c -> {
        assertThat(c.context().traceIdString()).isEqualTo(parent.traceIdString());
        assertThat(c.context().parentIdString()).isEqualTo(parent.spanIdString());
      })
      .isInstanceOf(RealSpan.class);
  }

  /** A child span is not sharing a span ID with its parent by definition */
  @Test public void newChild_isntShared() {
    TraceContext parent = tracer.newTrace().context();

    assertThat(tracer.newChild(parent).context().shared())
      .isFalse();
  }

  @Test public void newChild_noop() {
    TraceContext parent = tracer.newTrace().context();

    tracer.noop.set(true);

    assertThat(tracer.newChild(parent))
      .isInstanceOf(NoopSpan.class);
  }

  @Test public void newChild_notSampledIsNoop() {
    TraceContext notSampled =
      tracer.newTrace().context().toBuilder().sampled(false).build();

    assertThat(tracer.newChild(notSampled))
      .isInstanceOf(NoopSpan.class);
  }

  @Test public void currentSpanCustomizer_defaultsToNoop() {
    assertThat(tracer.currentSpanCustomizer())
      .isSameAs(NoopSpanCustomizer.INSTANCE);
  }

  @Test public void currentSpanCustomizer_noop_when_notSampled() {
    ScopedSpan parent = tracer.withSampler(Sampler.NEVER_SAMPLE).startScopedSpan("parent");
    try {
      assertThat(tracer.currentSpanCustomizer())
        .isSameAs(NoopSpanCustomizer.INSTANCE);
    } finally {
      parent.finish();
    }
  }

  @Test public void currentSpanCustomizer_notSampled() {
    tracer = Tracing.newBuilder().sampler(Sampler.NEVER_SAMPLE).build().tracer();

    ScopedSpan parent = tracer.startScopedSpan("parent");
    try {
      assertThat(tracer.currentSpanCustomizer())
        .isSameAs(NoopSpanCustomizer.INSTANCE);
    } finally {
      parent.finish();
    }
  }

  @Test public void currentSpanCustomizer_real_when_sampled() {
    ScopedSpan parent = tracer.startScopedSpan("parent");

    try {
      assertThat(tracer.currentSpanCustomizer())
        .isInstanceOf(SpanCustomizerShield.class);
    } finally {
      parent.finish();
    }
  }

  @Test public void currentSpan_defaultsToNull() {
    assertThat(tracer.currentSpan()).isNull();
  }

  @Test public void currentSpan_decoratesExternalContext() {
    try (Scope ws = currentTraceContext.newScope(context)) {
      assertThat(tracer.currentSpan().context())
        .isNotSameAs(context)
        .extracting(TraceContext::localRootId)
        .isEqualTo(context.spanId());
    }
  }

  @Test public void currentSpan_retainsSharedFlag() {
    TraceContext context =
      TraceContext.newBuilder().traceId(1L).spanId(2L).shared(true).sampled(true).build();

    try (Scope ws = currentTraceContext.newScope(context)) {
      assertThat(tracer.currentSpan().context().shared()).isTrue();
    }
  }

  @Test public void currentSpan_doesntSetSharedFlag() {
    try (Scope ws = currentTraceContext.newScope(context)) {
      assertThat(tracer.currentSpan().context().shared()).isFalse();
    }
  }

  @Test public void currentSpan_doesntRedundantlyDecorateContext() {
    TraceContext context = tracer.newTrace().context();

    try (Scope ws = currentTraceContext.newScope(context)) {
      assertThat(tracer.currentSpan().context())
        .isSameAs(context)
        .extracting(TraceContext::localRootId)
        .isEqualTo(context.spanId());
    }
  }

  @Test public void nextSpan_defaultsToMakeNewTrace() {
    assertThat(tracer.nextSpan().context().parentId()).isNull();
    assertThat(tracer.nextSpan(deferDecision(), false).context().parentId()).isNull();
  }

  @Test public void nextSpan_usesSampler() {
    assertThat(tracer.nextSpan().context().parentId()).isNull();
    assertThat(tracer.nextSpan(neverSample(), false).context().sampled()).isFalse();
  }

  @Test public void nextSpanWithParent() {
    Span span = tracer.nextSpanWithParent(deferDecision(), false, context);

    assertThat(span.context().parentId()).isEqualTo(context.spanId());
  }

  @Test public void nextSpanWithParent_overrideToMakeNewTrace() {
    Span span;
    try (Scope ws = currentTraceContext.newScope(context)) {
      span = tracer.nextSpanWithParent(deferDecision(), false, null);
    }

    assertThat(span.context().parentId()).isNull();
  }

  @Test public void nextSpan_extractedNothing_makesChildOfCurrent() {
    Span parent = tracer.newTrace();

    try (SpanInScope ws = tracer.withSpanInScope(parent)) {
      Span nextSpan = tracer.nextSpan(TraceContextOrSamplingFlags.create(EMPTY));
      assertThat(nextSpan.context().parentId())
        .isEqualTo(parent.context().spanId());
    }
  }

  @Test public void nextSpan_extractedNothing_defaultsToMakeNewTrace() {
    Span nextSpan = tracer.nextSpan(TraceContextOrSamplingFlags.create(EMPTY));

    assertThat(nextSpan.context().parentId())
      .isNull();
  }

  @Test public void nextSpan_makesChildOfCurrent() {
    Span parent = tracer.newTrace();

    try (SpanInScope ws = tracer.withSpanInScope(parent)) {
      assertThat(tracer.nextSpan().context().parentId())
        .isEqualTo(parent.context().spanId());
    }
  }

  @Test public void nextSpan_extractedExtra_newTrace() {
    TraceContextOrSamplingFlags extracted =
      TraceContextOrSamplingFlags.newBuilder(EMPTY).addExtra(1L).build();

    assertThat(tracer.nextSpan(extracted).context().extra())
      .containsExactly(1L);
  }

  @Test public void nextSpan_extractedExtra_childOfCurrent() {
    Span parent = tracer.newTrace();

    TraceContextOrSamplingFlags extracted =
      TraceContextOrSamplingFlags.newBuilder(EMPTY).addExtra(1L).build();

    try (SpanInScope ws = tracer.withSpanInScope(parent)) {
      assertThat(tracer.nextSpan(extracted).context().extra())
        .containsExactly(1L);
    }
  }

  @Test public void nextSpan_extractedExtra_appendsToChildOfCurrent() {
    // current parent already has extra stuff
    Span parent = tracer.nextSpan(TraceContextOrSamplingFlags.newBuilder(EMPTY)
      .addExtra(1L)
      .build());

    TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.newBuilder(EMPTY)
      .addExtra(1F)
      .build();

    try (SpanInScope ws = tracer.withSpanInScope(parent)) {
      assertThat(tracer.nextSpan(extracted).context().extra())
        .containsExactlyInAnyOrder(1L, 1F);
    }
  }

  @Test public void nextSpan_extractedTraceId() {
    TraceIdContext traceIdContext = TraceIdContext.newBuilder().traceId(1L).build();
    TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.create(traceIdContext);

    assertThat(tracer.nextSpan(extracted).context().traceId())
      .isEqualTo(1L);
  }

  @Test public void nextSpan_extractedTraceId_extra() {
    TraceIdContext traceIdContext = TraceIdContext.newBuilder().traceId(1L).build();
    TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.newBuilder(traceIdContext)
      .addExtra(1L).build();

    assertThat(tracer.nextSpan(extracted).context().extra())
      .containsExactly(1L);
  }

  @Test public void nextSpan_extractedTraceContext() {
    TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.create(context);

    assertThat(tracer.nextSpan(extracted).context())
      .extracting(TraceContext::traceId, TraceContext::parentId)
      .containsExactly(1L, 2L);
  }

  @Test public void nextSpan_extractedTraceContext_extra() {
    TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.newBuilder(context)
      .addExtra(1L).build();

    assertThat(tracer.nextSpan(extracted).context().extra())
      .contains(1L);
  }

  @Test public void startScopedSpan_isInScope() {
    assertRealRoot(tracer.startScopedSpan("foo"));
    assertRealRoot(tracer.startScopedSpan("foo", deferDecision(), false));
  }

  void assertRealRoot(ScopedSpan current) {
    try {
      assertThat(tracer.currentSpan().context())
        .isEqualTo(current.context());
      assertThat(tracer.currentSpan().context().parentIdAsLong())
        .isZero();
      assertThat(tracer.currentSpanCustomizer())
        .isNotEqualTo(NoopSpanCustomizer.INSTANCE);
    } finally {
      current.finish();
    }

    // context was cleared
    assertThat(tracer.currentSpan()).isNull();
  }

  @Test public void startScopedSpan_noopIsInScope() {
    ScopedSpan current = tracer.startScopedSpan("foo", neverSample(), false);

    try {
      assertThat(tracer.currentSpan().context())
        .isEqualTo(current.context());
      assertThat(tracer.currentSpanCustomizer())
        .isSameAs(NoopSpanCustomizer.INSTANCE);
    } finally {
      current.finish();
    }

    // context was cleared
    assertThat(tracer.currentSpan()).isNull();
  }

  @Test public void withSpanInScope() {
    Span current = tracer.newTrace();

    try (SpanInScope ws = tracer.withSpanInScope(current)) {
      assertThat(tracer.currentSpan())
        .isEqualTo(current);
      assertThat(tracer.currentSpanCustomizer())
        .isNotEqualTo(current)
        .isNotEqualTo(NoopSpanCustomizer.INSTANCE);
    }

    // context was cleared
    assertThat(tracer.currentSpan()).isNull();
  }

  @Test public void withNoopSpanInScope() {
    Span current = tracer.withSampler(Sampler.NEVER_SAMPLE).nextSpan();

    try (SpanInScope ws = tracer.withSpanInScope(current)) {
      assertThat(tracer.currentSpan())
        .isEqualTo(current);
      assertThat(tracer.currentSpanCustomizer())
        .isNotEqualTo(current)
        .isEqualTo(NoopSpanCustomizer.INSTANCE);
    }

    // context was cleared
    assertThat(tracer.currentSpan()).isNull();
  }

  @Test public void toString_withSpanInScope() {
    TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(10L).sampled(true).build();
    try (SpanInScope ws = tracer.withSpanInScope(tracer.toSpan(context))) {
      assertThat(tracer.toString()).hasToString(
        "Tracer{currentSpan=0000000000000001/000000000000000a, spanHandler=[TestSpanHandler{[]}, OrphanTracker{}]}"
      );
    }
  }

  @Test public void toString_whenNoop() {
    Tracing.current().setNoop(true);

    assertThat(tracer).hasToString(
      "Tracer{noop=true, spanHandler=[TestSpanHandler{[]}, OrphanTracker{}]}"
    );
  }

  @Test public void toString_withSpanReporter() {
    tracer = Tracing.newBuilder().addSpanHandler(new SpanHandler() {
      @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
        return true;
      }

      @Override public String toString() {
        return "MyHandler";
      }
    }).spanReporter(new Reporter<zipkin2.Span>() {
      @Override public void report(zipkin2.Span span) {
      }

      @Override public String toString() {
        return "MyReporter";
      }
    }).build().tracer();

    assertThat(tracer).hasToString(
      "Tracer{spanHandler=[MyHandler, MyReporter]}"
    );
  }

  @Test public void withSpanInScope_nested() {
    Span parent = tracer.newTrace();

    try (SpanInScope wsParent = tracer.withSpanInScope(parent)) {

      Span child = tracer.newChild(parent.context());
      try (SpanInScope wsChild = tracer.withSpanInScope(child)) {
        assertThat(tracer.currentSpan())
          .isEqualTo(child);
      }

      // old parent reverted
      assertThat(tracer.currentSpan())
        .isEqualTo(parent);
    }
  }

  @Test public void withSpanInScope_clear() {
    Span parent = tracer.newTrace();

    try (SpanInScope wsParent = tracer.withSpanInScope(parent)) {
      try (SpanInScope clearScope = tracer.withSpanInScope(null)) {
        assertThat(tracer.currentSpan())
          .isNull();
        assertThat(tracer.currentSpanCustomizer())
          .isEqualTo(NoopSpanCustomizer.INSTANCE);
      }

      // old parent reverted
      assertThat(tracer.currentSpan())
        .isEqualTo(parent);
    }
  }

  @Test public void join_getsExtraFromPropagationFactory() {
    propagationFactory = baggageFactory;

    TraceContext context = tracer.nextSpan().context();
    BAGGAGE_FIELD.updateValue(context, "napkin");

    TraceContext joined = tracer.joinSpan(context).context();

    assertThat(BAGGAGE_FIELD.getValue(joined)).isEqualTo("napkin");
  }

  @Test public void nextSpan_getsExtraFromPropagationFactory() {
    propagationFactory = baggageFactory;

    Span parent = tracer.nextSpan();
    BAGGAGE_FIELD.updateValue(parent.context(), "napkin");

    TraceContext nextSpan;
    try (SpanInScope scope = tracer.withSpanInScope(parent)) {
      nextSpan = tracer.nextSpan().context();
    }

    assertThat(BAGGAGE_FIELD.getValue(nextSpan)).isEqualTo("napkin");
  }

  @Test public void newChild_getsExtraFromPropagationFactory() {
    propagationFactory = baggageFactory;

    TraceContext context = tracer.nextSpan().context();
    BAGGAGE_FIELD.updateValue(context, "napkin");

    TraceContext newChild = tracer.newChild(context).context();

    assertThat(BAGGAGE_FIELD.getValue(newChild)).isEqualTo("napkin");
  }

  @Test public void startScopedSpanWithParent_getsExtraFromPropagationFactory() {
    propagationFactory = baggageFactory;

    TraceContext context = tracer.nextSpan().context();
    BAGGAGE_FIELD.updateValue(context, "napkin");

    ScopedSpan scoped = tracer.startScopedSpanWithParent("foo", context);
    scoped.finish();

    assertThat(BAGGAGE_FIELD.getValue(scoped.context())).isEqualTo("napkin");
  }

  @Test public void startScopedSpan_getsExtraFromPropagationFactory() {
    propagationFactory = baggageFactory;

    Span parent = tracer.nextSpan();
    BAGGAGE_FIELD.updateValue(parent.context(), "napkin");

    ScopedSpan scoped;
    try (SpanInScope scope = tracer.withSpanInScope(parent)) {
      scoped = tracer.startScopedSpan("foo");
      scoped.finish();
    }

    assertThat(BAGGAGE_FIELD.getValue(scoped.context())).isEqualTo("napkin");
  }

  @Test public void startScopedSpan() {
    ScopedSpan scoped = tracer.startScopedSpan("foo");
    try {
      assertThat(tracer.currentTraceContext.get()).isSameAs(scoped.context());
    } finally {
      scoped.finish();
    }

    assertThat(spans.get(0).name())
      .isEqualTo("foo");
    assertThat(spans.get(0).finishTimestamp())
      .isPositive();
  }

  @Test public void startScopedSpan_overrideName() {
    ScopedSpan scoped = tracer.startScopedSpan("foo");
    try {
      scoped.name("bar");
    } finally {
      scoped.finish();
    }

    assertThat(spans.get(0).name()).isEqualTo("bar");
  }

  @Test public void useSpanAfterFinished_doesNotCauseBraveFlush() {
    simulateInProcessPropagation(tracer, tracer);
    GarbageCollectors.blockOnGC();
    tracer.newTrace().start().abandon(); //trigger orphaned span check
    assertThat(spans).hasSize(1);
    assertThat(spans.get(0).annotations())
      .extracting(Map.Entry::getValue)
      .doesNotContain("brave.flush");
  }

  @Test public void useSpanAfterFinishedInOtherTracer_doesNotCauseBraveFlush() {
    Tracer noOpTracer = Tracing.newBuilder().spanReporter(s -> {
      // intentionally avoid any special-casing of Reporter.NOOP
    }).build().tracer();
    simulateInProcessPropagation(noOpTracer, tracer);
    GarbageCollectors.blockOnGC();
    tracer.newTrace().start().abandon(); //trigger orphaned span check

    // We expect the span to be reported to the NOOP reporter, and nothing to be reported to "spans"
    assertThat(spans).isEmpty();
  }

  /**
   * Must be a separate method from the test method to allow for local variables to be garbage
   * collected
   */
  private static void simulateInProcessPropagation(Tracer tracer1, Tracer tracer2) {
    Span span1 = tracer1.newTrace();
    span1.start();

    // Pretend we're on child thread
    Tracer.SpanInScope spanInScope = tracer2.withSpanInScope(span1);

    // Back on original thread
    span1.finish();

    // Pretend we're on child thread
    Span span2 = tracer2.currentSpan();
    spanInScope.close();
  }

  @Test public void newTrace_resultantSpanIsLocalRoot() {
    Span span = tracer.newTrace();

    assertThat(span.context().spanId()).isEqualTo(span.context().localRootId()); // Sanity check
    assertThat(span.context().isLocalRoot()).isTrue();
  }

  @Test public void joinSpan_whenJoinIsSupported_resultantSpanIsLocalRoot() {
    tracer = Tracing.newBuilder().supportsJoin(true).build().tracer();

    Span span = tracer.joinSpan(context);

    assertThat(span.context().spanId()).isEqualTo(span.context().localRootId()); // Sanity check
    assertThat(span.context().isLocalRoot()).isTrue();

    // Bad behaviour, but if someone uses joinSpan in the same process, it isn't a local root
    Span child = tracer.joinSpan(span.context());
    assertThat(child.context().localRootId()).isEqualTo(span.context().localRootId());
    assertThat(child.context().isLocalRoot()).isFalse();
  }

  @Test public void joinSpan_whenJoinIsNotSupported_resultantSpanIsLocalRoot() {
    tracer = Tracing.newBuilder().supportsJoin(false).build().tracer();

    Span span = tracer.joinSpan(context);

    assertThat(span.context().spanId()).isEqualTo(span.context().localRootId()); // Sanity check
    assertThat(span.context().isLocalRoot()).isTrue();
  }

  @Test public void startScopedSpan_resultantSpanIsLocalRoot() {
    ScopedSpan span = tracer.startScopedSpan("foo");
    try {
      assertThat(span.context().spanId()).isEqualTo(span.context().localRootId()); // Sanity check
      assertThat(span.context().isLocalRoot()).isTrue();

      ScopedSpan child = tracer.startScopedSpan("bar");
      try {

        // Check we don't always make children local roots
        assertThat(child.context().localRootId()).isEqualTo(span.context().localRootId());
        assertThat(child.context().isLocalRoot()).isFalse();
      } finally {
        child.finish();
      }
    } finally {
      span.finish();
    }
  }

  @Test public void startScopedSpanWithParent_ignoresCurrentSpan() {
    ScopedSpan span = tracer.startScopedSpan("foo");
    try {
      ScopedSpan child = tracer.startScopedSpanWithParent("bar", null);
      try {
        assertThat(child.context().parentIdAsLong()).isZero();
        assertThat(child.context().traceIdString()).isNotEqualTo(span.context().traceIdString());
      } finally {
        child.finish();
      }
    } finally {
      span.finish();
    }
  }

  @Test public void startScopedSpanWithParent_resultantSpanIsLocalRoot() {
    ScopedSpan span = tracer.startScopedSpanWithParent("foo", context);
    try {
      assertThat(span.context().spanId()).isEqualTo(span.context().localRootId()); // Sanity check
      assertThat(span.context().isLocalRoot()).isTrue();

      ScopedSpan child = tracer.startScopedSpanWithParent("bar", span.context());
      try {

        // Check we don't always make children local roots
        assertThat(child.context().localRootId()).isEqualTo(span.context().localRootId());
        assertThat(child.context().isLocalRoot()).isFalse();
      } finally {
        child.finish();
      }
    } finally {
      span.finish();
    }
  }

  @Test public void newChild_resultantSpanIsLocalRoot() {
    Span span = tracer.newChild(context);

    assertThat(span.context().spanId()).isEqualTo(span.context().localRootId()); // Sanity check
    assertThat(span.context().isLocalRoot()).isTrue();

    // Check we don't always make children local roots
    Span child = tracer.newChild(span.context());
    assertThat(child.context().localRootId()).isEqualTo(span.context().localRootId());
    assertThat(child.context().isLocalRoot()).isFalse();
  }

  @Test public void joinSpan_notYetSampledIsNotShared_root() {
    Span span = tracer.joinSpan(context);

    assertThat(span.context().shared()).isFalse();
  }

  @Test public void joinSpan_notYetSampledIsNotShared_child() {
    TraceContext context =
      TraceContext.newBuilder().traceId(1).parentId(2).spanId(3).shared(true).build();
    Span span = tracer.joinSpan(context);

    assertThat(span.context().shared()).isFalse();
  }

  @Test public void newChild_childOfLocalRootIsNotShared() {
    TraceContext context =
      TraceContext.newBuilder().traceId(1).spanId(2).shared(true).sampled(true).build();
    Span span = tracer.joinSpan(context);

    assertThat(span.context().spanId()).isEqualTo(span.context().localRootId()); // Sanity check
    assertThat(span.context().shared()).isTrue();
    assertThat(span.context().isLocalRoot()).isTrue();

    // Check we don't inherit the shared flag
    Span child = tracer.newChild(span.context());
    assertThat(child.context().localRootId()).isEqualTo(span.context().localRootId());
    assertThat(child.context().shared()).isFalse();
    assertThat(child.context().isLocalRoot()).isFalse();
  }

  @Test public void nextSpan_fromFlags_resultantSpanIsLocalRoot() {
    Span span = tracer.nextSpan(TraceContextOrSamplingFlags.create(SamplingFlags.SAMPLED));

    assertThat(span.context().spanId()).isEqualTo(span.context().localRootId()); // Sanity check
    assertThat(span.context().isLocalRoot()).isTrue();
  }

  @Test public void nextSpan_fromContext_resultantSpanIsLocalRoot() {
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).build();
    Span span = tracer.nextSpan(TraceContextOrSamplingFlags.create(context));

    assertThat(span.context().spanId()).isEqualTo(span.context().localRootId()); // Sanity check
    assertThat(span.context().isLocalRoot()).isTrue();

    // Bad behaviour, but if someone uses extract in the same process, it isn't a local root
    Span child = tracer.nextSpan(TraceContextOrSamplingFlags.create(span.context()));
    assertThat(child.context().localRootId()).isEqualTo(span.context().localRootId());
    assertThat(child.context().isLocalRoot()).isFalse();
  }

  @Test public void nextSpan_fromIdContext_resultantSpanIsLocalRoot() {
    TraceIdContext context = TraceIdContext.newBuilder().traceId(1).build();
    Span span = tracer.nextSpan(TraceContextOrSamplingFlags.create(context));

    assertThat(span.context().spanId()).isEqualTo(span.context().localRootId()); // Sanity check
    assertThat(span.context().isLocalRoot()).isTrue();
  }

  @Test public void localRootId_joinSpan_notYetSampled() {
    TraceContext context1 = TraceContext.newBuilder().traceId(1).spanId(2).build();
    TraceContext context2 = TraceContext.newBuilder().traceId(1).spanId(3).build();
    localRootId(context1, context2, ctx -> tracer.joinSpan(ctx.context()));
  }

  @Test public void localRootId_joinSpan_notSampled() {
    TraceContext context1 = TraceContext.newBuilder().traceId(1).spanId(2).sampled(false).build();
    TraceContext context2 = TraceContext.newBuilder().traceId(1).spanId(3).sampled(false).build();
    localRootId(context1, context2, ctx -> tracer.joinSpan(ctx.context()));
  }

  @Test public void localRootId_joinSpan_sampled() {
    TraceContext context1 = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    TraceContext context2 = TraceContext.newBuilder().traceId(1).spanId(3).sampled(true).build();
    localRootId(context1, context2, ctx -> tracer.joinSpan(ctx.context()));
  }

  @Test public void localRootId_nextSpan_notYetSampled() {
    TraceContext context1 = TraceContext.newBuilder().traceId(1).spanId(2).build();
    TraceContext context2 = TraceContext.newBuilder().traceId(1).spanId(3).build();
    localRootId(context1, context2, ctx -> tracer.nextSpan(ctx));
  }

  @Test public void localRootId_nextSpan_notSampled() {
    TraceContext context1 = TraceContext.newBuilder().traceId(1).spanId(2).sampled(false).build();
    TraceContext context2 = TraceContext.newBuilder().traceId(1).spanId(3).sampled(false).build();
    localRootId(context1, context2, ctx -> tracer.nextSpan(ctx));
  }

  @Test public void localRootId_nextSpan_sampled() {
    TraceContext context1 = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    TraceContext context2 = TraceContext.newBuilder().traceId(1).spanId(3).sampled(true).build();
    localRootId(context1, context2, ctx -> tracer.nextSpan(ctx));
  }

  @Test public void localRootId_nextSpan_ids_notYetSampled() {
    TraceIdContext context1 = TraceIdContext.newBuilder().traceId(1).build();
    TraceIdContext context2 = TraceIdContext.newBuilder().traceId(2).build();
    localRootId(context1, context2, ctx -> tracer.nextSpan(ctx));
  }

  @Test public void localRootId_nextSpan_ids_notSampled() {
    TraceIdContext context1 = TraceIdContext.newBuilder().traceId(1).sampled(false).build();
    TraceIdContext context2 = TraceIdContext.newBuilder().traceId(2).sampled(false).build();
    localRootId(context1, context2, ctx -> tracer.nextSpan(ctx));
  }

  @Test public void localRootId_nextSpan_ids_sampled() {
    TraceIdContext context1 = TraceIdContext.newBuilder().traceId(1).sampled(true).build();
    TraceIdContext context2 = TraceIdContext.newBuilder().traceId(2).sampled(true).build();
    localRootId(context1, context2, ctx -> tracer.nextSpan(ctx));
  }

  @Test public void localRootId_nextSpan_flags_empty() {
    TraceContextOrSamplingFlags flags = TraceContextOrSamplingFlags.EMPTY;
    localRootId(flags, flags, ctx -> tracer.nextSpan(ctx));
  }

  @Test public void localRootId_nextSpan_flags_notSampled() {
    TraceContextOrSamplingFlags flags = TraceContextOrSamplingFlags.NOT_SAMPLED;
    localRootId(flags, flags, ctx -> tracer.nextSpan(ctx));
  }

  @Test public void localRootId_nextSpan_flags_sampled() {
    TraceContextOrSamplingFlags flags = TraceContextOrSamplingFlags.SAMPLED;
    localRootId(flags, flags, ctx -> tracer.nextSpan(ctx));
  }

  @Test public void localRootId_nextSpan_flags_debug() {
    TraceContextOrSamplingFlags flags = TraceContextOrSamplingFlags.DEBUG;
    localRootId(flags, flags, ctx -> tracer.nextSpan(ctx));
  }

  @Test public void joinSpan_decorates() {
    propagationFactory = baggageFactory;
    TraceContext incoming = TraceContext.newBuilder().traceId(1L).spanId(2L).sampled(true)
      .shared(true).build();

    TraceContext joined = tracer.joinSpan(incoming).context();
    assertThat(joined).isNotSameAs(incoming);
    assertThat(joined.extra()).isNotEmpty();
  }

  @Test public void joinSpan_decorates_unsampled() {
    propagationFactory = baggageFactory;
    TraceContext incoming = TraceContext.newBuilder().traceId(1L).spanId(2L).sampled(false)
      .shared(true).build();

    TraceContext joined = tracer.joinSpan(incoming).context();
    assertThat(joined).isNotSameAs(incoming);
    assertThat(joined.extra()).isNotEmpty();
  }

  @Test public void toSpan_decorates() {
    propagationFactory = baggageFactory;
    TraceContext incoming = TraceContext.newBuilder().traceId(1L).spanId(2L).sampled(true).build();

    TraceContext toSpan = tracer.toSpan(incoming).context();
    assertThat(toSpan).isNotSameAs(incoming);
    assertThat(toSpan.extra()).isNotEmpty();
  }

  @Test public void toSpan_decorates_unsampled() {
    propagationFactory = baggageFactory;
    TraceContext incoming = TraceContext.newBuilder().traceId(1L).spanId(2L).sampled(false).build();

    TraceContext toSpan = tracer.toSpan(incoming).context();
    assertThat(toSpan).isNotSameAs(incoming);
    assertThat(toSpan.extra()).isNotEmpty();
  }

  @Test public void currentSpan_sameContextReference() {
    Span span = tracer.newTrace();
    try (SpanInScope ws = tracer.withSpanInScope(span)) {
      assertThat(tracer.currentSpan().context())
        .isSameAs(span.context());
    }
  }

  @Test public void join_idempotent() {
    TraceContext incoming = TraceContext.newBuilder().traceId(1L).spanId(2L).sampled(true)
      .shared(true).build();

    TraceContext joined = tracer.joinSpan(incoming).context();
    assertThat(joined).isNotSameAs(incoming);
    assertThat(tracer.joinSpan(incoming).context()).isSameAs(joined);
  }

  @Test public void join_idempotent_unsampled() {
    TraceContext incoming = TraceContext.newBuilder().traceId(1L).spanId(2L).sampled(false)
      .shared(true).build();

    TraceContext joined = tracer.joinSpan(incoming).context();
    assertThat(joined).isNotSameAs(incoming);
    assertThat(tracer.joinSpan(incoming).context())
      .isNotSameAs(joined); // unsampled spans are by definition not tracked in pending spans
  }

  @Test public void toSpan_idempotent() {
    TraceContext incoming = TraceContext.newBuilder().traceId(1L).spanId(2L).sampled(true).build();

    TraceContext toSpan = tracer.toSpan(incoming).context();
    assertThat(toSpan).isNotSameAs(incoming);
    assertThat(tracer.toSpan(incoming).context()).isSameAs(toSpan);
  }

  @Test public void toSpan_idempotent_unsampled() {
    TraceContext incoming = TraceContext.newBuilder().traceId(1L).spanId(2L).sampled(false).build();

    TraceContext toSpan = tracer.toSpan(incoming).context();
    assertThat(toSpan).isNotSameAs(incoming);
    assertThat(tracer.toSpan(incoming).context())
      .isNotSameAs(toSpan); // unsampled spans are by definition not tracked in pending spans
  }

  void localRootId(TraceContext c1, TraceContext c2,
    Function<TraceContextOrSamplingFlags, Span> fn) {
    localRootId(TraceContextOrSamplingFlags.create(c1), TraceContextOrSamplingFlags.create(c2), fn);
  }

  void localRootId(TraceIdContext c1, TraceIdContext c2,
    Function<TraceContextOrSamplingFlags, Span> fn) {
    localRootId(TraceContextOrSamplingFlags.create(c1), TraceContextOrSamplingFlags.create(c2), fn);
  }

  void localRootId(TraceContextOrSamplingFlags ctx1, TraceContextOrSamplingFlags ctx2,
    Function<TraceContextOrSamplingFlags, Span> ctxFn
  ) {
    Map<Long, List<String>> reportedNames = tracerThatPartitionsNamesOnlocalRootId();
    Span server1 = ctxFn.apply(ctx1).name("server1").kind(SERVER).start();
    Span server2 = ctxFn.apply(ctx2).name("server2").kind(SERVER).start();
    try {
      Span client1 = tracer.newChild(server1.context()).name("client1").kind(CLIENT).start();
      ScopedSpan processor1 = tracer.startScopedSpanWithParent("processor1", server1.context());
      try {
        try {
          ScopedSpan processor2 = tracer.startScopedSpanWithParent("processor2", server2.context());
          try {
            tracer.nextSpan().name("client2").kind(CLIENT).start().finish();
            tracer.nextSpan().name("client3").kind(CLIENT).start().finish();
          } finally {
            processor2.finish();
          }
        } finally {
          server2.finish();
        }
      } finally {
        client1.finish();
        processor1.finish();
      }
    } finally {
      server1.finish();
    }

    assertThat(reportedNames).hasSize(2).containsValues(
      asList("client1", "processor1", "server1"),
      asList("client2", "client3", "processor2", "server2")
    );
  }

  Map<Long, List<String>> tracerThatPartitionsNamesOnlocalRootId() {
    Map<Long, List<String>> reportedNames = new LinkedHashMap<>();
    tracer = Tracing.newBuilder().addSpanHandler(new SpanHandler() {
      @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
        assertThat(context.localRootId()).isNotZero();
        reportedNames.computeIfAbsent(context.localRootId(), k -> new ArrayList<>())
          .add(span.name());
        return true; // retain
      }
    }).alwaysSampleLocal().build().tracer();
    return reportedNames;
  }
}
