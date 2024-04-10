/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.internal;

public final class RecyclableBuffers {

  private static final ThreadLocal<char[]> PARSE_BUFFER = new ThreadLocal<char[]>();

  /**
   * Returns a {@link ThreadLocal} reused {@code char[]} for use when decoding bytes into an ID hex
   * string. The buffer should be immediately copied into a {@link String} after decoding within the
   * same method.
   */
  public static char[] parseBuffer() {
    char[] idBuffer = PARSE_BUFFER.get();
    if (idBuffer == null) {
      idBuffer = new char[32 + 1 + 16 + 3 + 16]; // traceid128-spanid-1-parentid
      PARSE_BUFFER.set(idBuffer);
    }
    return idBuffer;
  }

  private RecyclableBuffers() {
  }
}
