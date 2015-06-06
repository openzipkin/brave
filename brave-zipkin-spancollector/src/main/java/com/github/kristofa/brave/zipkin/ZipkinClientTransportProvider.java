package com.github.kristofa.brave.zipkin;

import org.apache.thrift.transport.TTransport;

/**
 * Provider of {@link org.apache.thrift.transport.TTransport transport} implementations.
 *
 * @author botizac
 */
public interface ZipkinClientTransportProvider {
    /**
     * Supplies a transport instance. Implementations are free to cache the objects returned or use a prototype approach.
     *
     * @return Client side Transport object
     */
    TTransport getTransport();
}
