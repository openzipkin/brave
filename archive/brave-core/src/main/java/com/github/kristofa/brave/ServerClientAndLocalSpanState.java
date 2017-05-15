package com.github.kristofa.brave;

/**
 * Combines server and client span state.
 * 
 * @author kristof
 *
 * @deprecated Replaced by {@code brave.propagation.CurrentTraceContext}
 */
@Deprecated
public interface ServerClientAndLocalSpanState extends ServerSpanState, ClientSpanState, LocalSpanState {

}
