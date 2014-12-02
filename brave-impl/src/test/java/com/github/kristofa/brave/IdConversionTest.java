package com.github.kristofa.brave;

import static org.junit.Assert.*;

import org.junit.Test;

public class IdConversionTest {
	
	@Test
	public void testPositiveId() {
		final long longId = 8828218016717761634l;
		// This id was generated using the zipkin code.
		final String expectedId = "7a842183262a6c62";
		assertEquals(expectedId, IdConversion.convertToString(longId));
		assertEquals(longId, IdConversion.convertToLong(expectedId));
	}
	

	@Test
	public void testNegativeId() {
		final long longId = -4667777584646200191l;
		// This id was generated using the zipkin code.
		final String expectedId = "bf38b90488a1e481";
		assertEquals(expectedId, IdConversion.convertToString(longId));
		assertEquals(longId, IdConversion.convertToLong(expectedId));
	}
	
	@Test
	public void testZeroId() {
		final long longId = 0;
		// Zipkin prepends 0's but conversion without those zeros also works.
		final String expectedId = "0";
		assertEquals(expectedId, IdConversion.convertToString(longId));
		assertEquals(longId, IdConversion.convertToLong(expectedId));
	}
	
	@Test
	public void testMinValueId() {
		final long longId = Long.MIN_VALUE;
		// This id was generated using the zipkin code.
		final String expectedId = "8000000000000000";
		assertEquals(expectedId, IdConversion.convertToString(longId));
		assertEquals(longId, IdConversion.convertToLong(expectedId));
	}
	
	@Test
	public void testMaxValueId() {
		final long longId = Long.MAX_VALUE;
		// This id was generated using the zipkin code.
		final String expectedId = "7fffffffffffffff";
		assertEquals(expectedId, IdConversion.convertToString(longId));
		assertEquals(longId, IdConversion.convertToLong(expectedId));
	}

}
