package com.github.kristofa.brave;

import com.google.common.primitives.UnsignedLongs;

/**
 * Contains conversion utilities for converting trace and span ids from long to string and vice
 * versa.
 * <p/>
 * The string representation is used when transferring trace context from client to server
 * as http headers.
 * <p/>
 * This implementation is expected to be compatible with Zipkin. This is important in case you
 * mix brave services with zipkin / finagle services.
 * <p/>
 * The only difference between the zipkin implementation and this implementation 
 * is that zipkin prepends '0' characters in case String does not contain 16 characters. 
 * For example instead of generating String 
 * "0" for long id 0 it generates "0000000000000000". But zipkin will properly convert String
 * representations in case they are not prepended with 0's. 
 * 
 * @author kristof
 */
public class IdConversion {
	
	/**
	 * Converts long trace or span id to String.
	 * 
	 * @param id trace, span or parent span id.
	 * @return String representation.
	 */
	public static String convertToString(final long id)
	{
		return UnsignedLongs.toString(id, 16);
	}
	
	/**
	 * Converts String trace or span id to long.
	 * 
	 * @param id trace, span or parent span id.
	 * @return Long representation.
	 */
	public static long convertToLong(final String id)
	{
		return UnsignedLongs.parseUnsignedLong(id, 16);
	}

}
