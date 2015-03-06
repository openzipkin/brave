package com.github.kristofa.brave.cxf;

/**
 * Created by fedor on 04.03.15.
 *
 * Limits the number of attempts to call complex risky non-critical code, so
 * the logs are not overfilled with error messages. Implementation should
 * be thread safe.
 */
public interface AttemptLimiter {

    //Call it to report successful attempt
    public void reportSuccess();

    //Call it to report failure
    public void reportFailure();

    //Call it before attempt to call complex code
    public boolean shouldTry();

}
