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
package brave.handler;

import brave.Tag;
import brave.internal.codec.JsonWriter;
import brave.internal.codec.ZipkinV2JsonWriter;
import brave.internal.codec.WriteBuffer;
import java.util.List;

/** Similar to {@code zipkin2.MutableSpan.SpanBytesEncoder} except no Zipkin dependency. */
public abstract class MutableSpanBytesEncoder {

  public static MutableSpanBytesEncoder zipkinJsonV2(Tag<Throwable> errorTag) {
    if (errorTag == null) throw new NullPointerException("errorTag == null");
    return new ZipkinJsonV2(errorTag);
  }

  public abstract int sizeInBytes(MutableSpan input);

  /** Serializes an object into its binary form. */
  public abstract byte[] encode(MutableSpan input);

  /** Serializes a list of objects into their binary form. */
  public abstract byte[] encodeList(List<MutableSpan> input);

  /** Allows you to encode a list of spans onto a specific offset. For example, when nesting */
  public abstract int encodeList(List<MutableSpan> spans, byte[] out, int pos);

  /** Corresponds to the Zipkin JSON v2 format */
  static final class ZipkinJsonV2 extends MutableSpanBytesEncoder {
    final WriteBuffer.Writer<MutableSpan> writer;

    ZipkinJsonV2(Tag<Throwable> errorTag) {
      writer = new ZipkinV2JsonWriter(errorTag);
    }

    @Override public int sizeInBytes(MutableSpan input) {
      return writer.sizeInBytes(input);
    }

    @Override public byte[] encode(MutableSpan span) {
      return JsonWriter.write(writer, span);
    }

    @Override public byte[] encodeList(List<MutableSpan> spans) {
      return JsonWriter.writeList(writer, spans);
    }

    @Override public int encodeList(List<MutableSpan> spans, byte[] out, int pos) {
      return JsonWriter.writeList(writer, spans, out, pos);
    }
  }
}
