/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
package brave.internal.codec;

import brave.internal.Platform;
import brave.internal.codec.WriteBuffer.Writer;
import java.nio.charset.Charset;
import java.util.List;

import static java.lang.String.format;

// Initially, a partial copy of zipkin2.internal.JsonCodec (the write side)
public final class JsonWriter {
  public static final Charset UTF_8 = Charset.forName("UTF-8");

  static <T> int sizeInBytes(Writer<T> writer, List<T> value) {
    int length = value.size();
    int sizeInBytes = 2; // []
    if (length > 1) sizeInBytes += length - 1; // comma to join elements
    for (int i = 0; i < length; i++) {
      sizeInBytes += writer.sizeInBytes(value.get(i));
    }
    return sizeInBytes;
  }

  /** Inability to encode is a programming bug. */
  public static <T> byte[] write(Writer<T> writer, T value) {
    byte[] result = new byte[writer.sizeInBytes(value)];
    WriteBuffer b = WriteBuffer.wrap(result);
    try {
      writer.write(value, b);
    } catch (RuntimeException e) {
      int lengthWritten = result.length;
      for (int i = 0; i < result.length; i++) {
        if (result[i] == 0) {
          lengthWritten = i;
          break;
        }
      }

      // Don't use value directly in the message, as its toString might be implemented using this
      // method. If that's the case, we'd stack overflow. Instead, emit what we've written so far.
      String message =
          format(
              "Bug found using %s to write %s as json. Wrote %s/%s bytes: %s",
              writer.getClass().getSimpleName(),
              value.getClass().getSimpleName(),
              lengthWritten,
              result.length,
              new String(result, 0, lengthWritten, UTF_8));
      throw Platform.get().assertionError(message, e);
    }
    return result;
  }

  public static <T> byte[] writeList(Writer<T> writer, List<T> value) {
    if (value.isEmpty()) return new byte[] {'[', ']'};
    byte[] result = new byte[sizeInBytes(writer, value)];
    writeList(writer, value, WriteBuffer.wrap(result));
    return result;
  }

  public static <T> int writeList(Writer<T> writer, List<T> value, byte[] out,
      int pos) {
    if (value.isEmpty()) {
      out[pos++] = '[';
      out[pos++] = ']';
      return 2;
    }
    int initialPos = pos;
    WriteBuffer result = WriteBuffer.wrap(out, pos);
    writeList(writer, value, result);
    return result.pos() - initialPos;
  }

  public static <T> void writeList(Writer<T> writer, List<T> value, WriteBuffer b) {
    b.writeByte('[');
    for (int i = 0, length = value.size(); i < length; ) {
      writer.write(value.get(i++), b);
      if (i < length) b.writeByte(',');
    }
    b.writeByte(']');
  }
}
