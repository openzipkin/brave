package com.github.kristofa.brave;

/**
 * Combines server and client span state.
 * 
 * @author kristof
 */
public interface ServerClientAndLocalSpanState extends ServerSpanState, ClientSpanState, LocalSpanState {

}
