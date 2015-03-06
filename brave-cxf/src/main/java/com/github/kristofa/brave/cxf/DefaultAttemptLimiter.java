package com.github.kristofa.brave.cxf;

/**
 * Created by fedor on 04.03.15.
 */
public class DefaultAttemptLimiter implements AttemptLimiter {
    @Override
    public void reportSuccess() {

    }

    @Override
    public void reportFailure() {

    }

    @Override
    public boolean shouldTry() {
        return true;
    }
}
