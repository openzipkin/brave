package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Nullable;
import com.google.auto.value.AutoValue;
import java.util.Random;

/** Internal code that affects the {@linkplain SpanId} type. */
@AutoValue
abstract class SpanIdFactory {

  static Builder builder() {
    return new AutoValue_SpanIdFactory.Builder()
        .traceId128Bit(false)
        .randomGenerator(new Random())
        .sampler(Sampler.ALWAYS_SAMPLE);
  }

  abstract Builder toBuilder();

  @AutoValue.Builder interface Builder {
    Builder randomGenerator(Random randomGenerator);

    Builder traceId128Bit(boolean traceId128Bit);

    Builder sampler(Sampler sampler);

    SpanIdFactory build();
  }

  abstract Random randomGenerator();

  abstract boolean traceId128Bit();

  abstract Sampler sampler();

  /** Returns the next span ID derived from the input, or a new trace if null. */
  SpanId next(@Nullable SpanId maybeParent) {
    long newSpanId = randomGenerator().nextLong();
    if (maybeParent == null) { // new trace
      return SpanId.builder()
          .traceIdHigh(traceId128Bit() ? randomGenerator().nextLong() : 0L)
          .traceId(newSpanId)
          .spanId(newSpanId)
          .sampled(sampler().isSampled(newSpanId))
          .build();
    }
    return maybeParent.toBuilder()
        .parentId(maybeParent.spanId)
        .spanId(newSpanId)
        .shared(false)
        .build();
  }

  /**
   * Joining is re-using the same trace and span ids extracted from an incoming request. Here, we
   * ensure a sampling decision has been made. If the span passed sampling, we assume this is a
   * shared span, one where the caller and the current tracer report to the same span IDs. If no
   * sampling decision occurred yet, we have exclusive access to this span ID.
   */
  SpanId join(SpanId context) {
    // If the sampled flag was left unset, we need to make the decision here
    if (context.sampled() == null) {
      return context.toBuilder()
          .sampled(sampler().isSampled(context.traceId))
          .shared(false)
          .build();
    } else if (context.sampled()) {
      // We know an instrumented caller initiated the trace if they sampled it
      return context.toBuilder().shared(true).build();
    } else {
      return context;
    }
  }
}
