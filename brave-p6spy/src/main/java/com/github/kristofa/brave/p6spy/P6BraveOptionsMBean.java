package com.github.kristofa.brave.p6spy;

/**
 * These values should match the what is expected by the {@link zipkin.Endpoint} class.
 */
public interface P6BraveOptionsMBean {

    /**
     * See {@link zipkin.Endpoint#ipv4}.
     *
     * @return the host
     */
    String getHost();

    /**
     * See {@link zipkin.Endpoint#port}.
     *
     * @return the port
     */
    String getPort();


    /**
     * See {@link zipkin.Endpoint#serviceName}.
     *
     * @return the service name
     */
    String getServiceName();

}
