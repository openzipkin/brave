package com.github.kristofa.brave;

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
  public static String convertToString(final long id) {
    return Long.toHexString(id);
  }

  /**
   * Parses a 1 to 32 character lower-hex string with no prefix into an unsigned long, tossing any
   * bits higher than 64.
   */
  public static long convertToLong(String lowerHex) {
    int length = lowerHex.length();
    if (length < 1 || length > 32) throw isntLowerHexLong(lowerHex);

    // trim off any high bits
    int beginIndex = length > 16 ? length - 16 : 0;

    return convertToLong(lowerHex, beginIndex);
  }

  /**
   * Parses a 16 character lower-hex string with no prefix into an unsigned long, starting at the
   * specified index.
   */
  public static long convertToLong(String lowerHex, int index) {
    long result = 0;
    for (int endIndex = Math.min(index + 16, lowerHex.length()); index < endIndex; index++) {
      char c = lowerHex.charAt(index);
      result <<= 4;
      if (c >= '0' && c <= '9') {
        result |= c - '0';
      } else if (c >= 'a' && c <= 'f') {
        result |= c - 'a' + 10;
      } else {
        throw isntLowerHexLong(lowerHex);
      }
    }
    return result;
  }

  static NumberFormatException isntLowerHexLong(String lowerHex) {
    throw new NumberFormatException(
        lowerHex + " should be a 1 to 32 character lower-hex string with no prefix");
  }
}
