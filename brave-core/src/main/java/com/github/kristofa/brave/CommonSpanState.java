package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Nullable;
import com.twitter.zipkin.gen.Endpoint;

/**
 * Keeps track of common trace/span state information.
 * <p>
 * Should be thread aware since we can have multiple parallel request which means multiple trace/spans.
 * </p>
 * 
 * @author kristof
 */
public interface CommonSpanState {

    /** @deprecated alias for the sampled flag on {@link ServerSpanState#getCurrentServerSpan()}. */
    @Deprecated
    @Nullable Boolean sample();

    /**
     * Gets the Endpoint (ip, port, service name) for this service.
     *
     * @return Endpoint for this service.
     */
    Endpoint endpoint();

}
