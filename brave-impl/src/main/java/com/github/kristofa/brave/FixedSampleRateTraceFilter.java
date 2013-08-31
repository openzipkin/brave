package com.github.kristofa.brave;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link TraceFilter} that is initialized with a fixed sample rate.
 * 
 * @author kristof
 */
public class FixedSampleRateTraceFilter implements TraceFilter {

    private final int sampleRate;
    private final AtomicInteger counter = new AtomicInteger();

    /**
     * Creates a new instance.
     * 
     * @param sampleRate Sample rate <= 0 means there will not be any tracing. Sample rate = 1 means every request will be
     *            traced. Sample rate > 1, for example 3 means 1 out of 3 requests will be traced.
     */
    public FixedSampleRateTraceFilter(final int sampleRate) {
        this.sampleRate = sampleRate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldTrace(final String requestName) {
        if (sampleRate <= 0) {
            return false;
        } else if (sampleRate == 1) {
            return true;
        }

        final int value = counter.incrementAndGet();
        if (value >= sampleRate) {
            synchronized (counter) {
                if (counter.get() >= sampleRate) {
                    counter.set(0);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        // Nothing to do here.

    }

}
