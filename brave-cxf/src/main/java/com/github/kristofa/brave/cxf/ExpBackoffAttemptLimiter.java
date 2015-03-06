package com.github.kristofa.brave.cxf;

/**
 * Created by fedor on 04.03.15.
 */
public class ExpBackoffAttemptLimiter implements AttemptLimiter {

    private volatile int failCount = 0;
    private volatile long lastTry = 0;
    private long baseTimeout = 1000L;
    private long maxTimeout = 1000*60*60L; //1 hour
    private long maxPower = (long)(Math.log(maxTimeout / baseTimeout) / Math.log(2) + 1);

    @Override
    public void reportSuccess() {
        lastTry = 0;
        failCount = 0;
    }

    @Override
    public void reportFailure() {
        lastTry = System.currentTimeMillis();
        failCount++;
    }

    @Override
    public boolean shouldTry() {
        if (failCount <= 0)
            return true;

        long elapsed = System.currentTimeMillis() - lastTry;
        long timeout = Math.min(baseTimeout * (1L << Math.min(failCount, maxPower)), maxTimeout);

        return (elapsed > timeout);
    }
}
