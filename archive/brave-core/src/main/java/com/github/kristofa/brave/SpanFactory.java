package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Nullable;
import com.google.auto.value.AutoValue;
import com.twitter.zipkin.gen.Span;
import java.util.Random;

/** Internal code that affects the {@linkplain Span} type. */
abstract class SpanFactory {
  /** Returns the next span ID derived from the input, or a new trace if null. */
  abstract Span nextSpan(@Nullable SpanId maybeParent);

  /**
   * Joining is re-using the same trace and span ids extracted from an incoming request. Here, we
   * ensure a sampling decision has been made. If the span passed sampling, we assume this is a
   * shared span, one where the caller and the current tracer report to the same span IDs. If no
   * sampling decision occurred yet, we have exclusive access to this span ID.
   */
  abstract Span joinSpan(SpanId context);

  @AutoValue
  static abstract class Default extends SpanFactory {

    static Builder builder() {
      return new AutoValue_SpanFactory_Default.Builder()
          .traceId128Bit(false)
          .supportsJoin(true)
          .randomGenerator(new Random())
          .sampler(Sampler.ALWAYS_SAMPLE);
    }

    abstract Builder toBuilder();

    @AutoValue.Builder interface Builder {
      Builder randomGenerator(Random randomGenerator);

      Builder traceId128Bit(boolean traceId128Bit);

      Builder supportsJoin(boolean supportsJoin);

      Builder sampler(Sampler sampler);

      Default build();
    }

    abstract Random randomGenerator();

    abstract boolean traceId128Bit();

    abstract boolean supportsJoin();

    abstract Sampler sampler();

    @Override Span nextSpan(@Nullable SpanId maybeParent) {
      long newSpanId = randomGenerator().nextLong();
      if (maybeParent == null) { // new trace
        return Brave.toSpan(SpanId.builder()
            .traceIdHigh(traceId128Bit() ? nextTraceIdHigh(randomGenerator()) : 0L)
            .traceId(newSpanId)
            .spanId(newSpanId)
            .sampled(sampler().isSampled(newSpanId))
            .build());
      }
      return Brave.toSpan(maybeParent.toBuilder()
          .parentId(maybeParent.spanId)
          .spanId(newSpanId)
          .shared(false)
          .build());
    }

    @Override Span joinSpan(SpanId context) {
      if (!supportsJoin()) return nextSpan(context);
      // If the sampled flag was left unset, we need to make the decision here
      if (context.sampled() == null) {
        return Brave.toSpan(context.toBuilder()
            .sampled(sampler().isSampled(context.traceId))
            .shared(false)
            .build());
      } else if (context.sampled()) {
        // We know an instrumented caller initiated the trace if they sampled it
        return Brave.toSpan(context.toBuilder().shared(true).build());
      } else {
        return Brave.toSpan(context);
      }
    }
  }

  static long nextTraceIdHigh(Random prng) {
    long epochSeconds = System.currentTimeMillis() / 1000;
    int random = prng.nextInt();
    return (epochSeconds & 0xffffffffL) << 32
        |  (random & 0xffffffffL);
  }
}

