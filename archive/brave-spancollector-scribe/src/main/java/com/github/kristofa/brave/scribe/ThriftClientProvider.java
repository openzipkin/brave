package com.github.kristofa.brave.scribe;

import org.apache.thrift.TException;

/**
 * The {@link ThriftClientProvider} is introduced mainly to be able to cope with temporary network connection issues.
 * 
 * @author kristof
 * @param <T> Client instance type.
 */
interface ThriftClientProvider<T> {

    /**
     * Initializes the client connection. This should only be done initially.. The idea is that if we end up with an issue
     * during initial connection setup the user can choose to abort.
     */
    void setup() throws TException;

    /**
     * Gets our client instance.
     * 
     * @return Client instance.
     */
    T getClient();

    /**
     * Logs an exception as a result of using the returned client. </p> Based on the exception we can decide to re-initialize
     * the client/connection to deal with for example temporary network connectivity issues.
     * 
     * @param exception Thrift exception.
     * @return New client instance in case we decided the exception could be dealt with by making a new client insance.
     *         <code>null</code> in case we decided a new client would not resolve the exception.
     */
    T exception(TException exception);

    /**
     * Closes the client. Once closed it will probably not be usable anymore.
     */
    void close();

}
