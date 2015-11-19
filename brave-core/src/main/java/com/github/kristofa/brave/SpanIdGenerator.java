package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Nullable;

import java.util.Random;

/**
 * Allows you to control how ids are generated. For example, implementations
 * can encode where in the call tree the span originates from, or use alternate
 * random number generators.
 */
// Intentionally single-method to be implementable with a lambda
public interface SpanIdGenerator {

    /** Returns the next trace id, or span id if parent is null. */
    SpanId nextSpanId(@Nullable SpanId parent);

    final class Default implements SpanIdGenerator {
        private final Random random = new Random();

        @Override
        public SpanId nextSpanId(@Nullable SpanId parent) {
            long nextLong = random.nextLong();
            if (parent == null) {
                return SpanId.create(nextLong, nextLong, null);
            }
            return SpanId.create(parent.getTraceId(), nextLong, parent.getSpanId());
        }
    }
}
