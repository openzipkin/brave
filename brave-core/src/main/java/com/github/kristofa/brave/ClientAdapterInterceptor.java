package com.github.kristofa.brave;

/**
 * Allows to intercept and modify the creation of  
 * {@link ServerRequestAdapter} and {@link ServerResponseAdapter}.
 */
public interface ClientAdapterInterceptor {

	/**
	 * Allows modification for {@link ServerRequestAdapter}.
	 * 
	 * @param interceptedAdapter obtained from the actual implementation or a preceding interceptor. 
	 * @return modified adapter to use.
	 */
	ServerRequestAdapter interceptRequestAdapter(ServerRequestAdapter interceptedAdapter);

	/**
	 * Allows modification for {@link ServerResponseAdapter}.
	 * 
	 * @param interceptedAdapter obtained from the actual implementation or a preceding interceptor. 
	 * @return modified adapter to use.
	 */
	ServerRequestAdapter interceptResponseAdapter(ServerRequestAdapter interceptedAdapter);

}
