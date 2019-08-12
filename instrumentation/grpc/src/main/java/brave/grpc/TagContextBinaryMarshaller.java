/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.grpc;

import brave.internal.Platform;
import io.grpc.Metadata.BinaryMarshaller;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This logs instead of throwing exceptions.
 *
 * <p>See
 * https://github.com/census-instrumentation/opencensus-java/blob/master/impl_core/src/main/java/io/opencensus/implcore/tags/propagation/SerializationUtils.java
 */
final class TagContextBinaryMarshaller implements BinaryMarshaller<Map<String, String>> {
  static final byte VERSION = 0, TAG_FIELD_ID = 0;
  static final byte[] EMPTY_BYTES = {};

  // grpc < 1.15 supports java 6 https://github.com/grpc/grpc-java/issues/3961
  static final Charset US_ASCII = Charset.forName("US-ASCII");

  @Override
  public byte[] toBytes(Map<String, String> tagContext) {
    if (tagContext == null) {
      throw new NullPointerException("tagContext == null"); // programming error
    }
    if (tagContext.isEmpty()) return EMPTY_BYTES;
    byte[] result = new byte[sizeInBytes(tagContext)];
    Buffer bytes = new Buffer(result);
    bytes.writeByte(VERSION);
    for (Map.Entry<String, String> entry : tagContext.entrySet()) {
      bytes.writeByte(TAG_FIELD_ID);
      bytes.writeLengthPrefixed(entry.getKey()); // TODO: should we check the result here?
      bytes.writeLengthPrefixed(entry.getValue());
    }
    return result;
  }

  @Override public Map<String, String> parseBytes(byte[] buf) {
    if (buf == null) throw new NullPointerException("buf == null"); // programming error
    if (buf.length == 0) return Collections.emptyMap();
    Buffer bytes = new Buffer(buf);
    byte version = bytes.readByte();
    if (version != VERSION) {
      Platform.get().log("Invalid input: unsupported version {0}", version, null);
      return null;
    }

    Map<String, String> result = new LinkedHashMap<>();
    while (bytes.remaining() > 3) { // tag for field ID and two lengths
      if (bytes.readByte() == TAG_FIELD_ID) {
        String key = bytes.readLengthPrefixed();
        if (key == null) break;
        String val = bytes.readLengthPrefixed();
        if (val == null) break;
        result.put(key, val);
      } else {
        Platform.get().log("Invalid input: expected TAG_FIELD_ID at offset {0}", bytes.pos, null);
        break;
      }
    }
    return result; // intentionally mutable
  }

  // like census, this currently assumes both key and value are ascii
  static int sizeInBytes(Map<String, String> tagContext) {
    int sizeInBytes = 1; // VERSION
    for (Map.Entry<String, String> entry : tagContext.entrySet()) {
      sizeInBytes++; // TAG_FIELD_ID
      int keyLength = entry.getKey().length();
      int valLength = entry.getValue().length();
      if (keyLength > 16383 || valLength > 16383) return sizeInBytes; // stop here
      sizeInBytes += sizeOfLengthPrefixedString(keyLength);
      sizeInBytes += sizeOfLengthPrefixedString(valLength);
    }
    return sizeInBytes;
  }

  static int sizeOfLengthPrefixedString(int length) {
    return (length > 127 ? 2 : 1) + length;
  }

  static final class Buffer {
    final byte[] buf;
    int pos;

    Buffer(byte[] buf) {
      this.buf = buf;
    }

    int remaining() {
      return buf.length - pos;
    }

    /** This needs to be checked externally to not overrun the underlying array */
    byte readByte() {
      return buf[pos++];
    }

    void writeByte(int v) {
      buf[pos++] = (byte) v;
    }

    /** Works only when values are ascii */
    boolean writeLengthPrefixed(String value) {
      int length = value.length();
      if (length > 16383) return false; // > 14bits is too big

      if (length > 127) { // varint encode over 2 bytes
        buf[pos++] = (byte) ((length & 0x7f) | 0x80);
        buf[pos++] = (byte) (length >>> 7);
      } else {
        buf[pos++] = (byte) length;
      }

      for (int i = 0; i < length; i++) {
        buf[pos++] = (byte) value.charAt(i);
      }

      return true;
    }

    String readLengthPrefixed() {
      byte b1 = buf[pos++];
      if (b1 >= 0) { // negative means MSB set
        return readAsciiString(b1);
      }
      return readAsciiString(readVarint(b1));
    }

    private int readVarint(byte b1) {
      int b2 = buf[pos++];
      if ((b2 & 0xf0) != 0) {
        Platform.get().log("Greater than 14-bit varint at position {0}", pos, null);
        return -1;
      }
      return (b1 & 0x7f) | b2 << 28;
    }

    String readAsciiString(int length) {
      if (length == -1 || remaining() < length) return null;

      String result = new String(buf, pos, length, US_ASCII);
      pos += length;
      return result;
    }
  }
}
