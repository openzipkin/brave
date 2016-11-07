package com.github.kristofa.brave;

/**
 * Allows to intercept and modify the creation of  
 * {@link ServerRequestAdapter} and {@link ServerResponseAdapter}.
 * Allows an application to submit additional annotations and
 * customize the behavior of the existing adapter implementation.
 */
public interface ServerAdapterInterceptor {

	/**
	 * Allows modification for {@link ServerRequestAdapter}.
	 * 
	 * @param originalAdapter obtained from actual implementation or a preceding filter. 
	 * @return modified adapter to use.
	 */
	ServerRequestAdapter interceptRequestAdapter(ServerRequestAdapter originalAdapter);

	/**
	 * Allows modification for {@link ServerResponseAdapter}.
	 * 
	 * @param originalAdapter obtained from actual implementation or a preceding filter. 
	 * @return modified adapter to use.
	 */
	ServerResponseAdapter interceptResponseAdapter(ServerResponseAdapter originalAdapter);

}
