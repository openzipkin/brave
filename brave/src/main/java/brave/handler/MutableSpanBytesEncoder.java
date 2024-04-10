/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.handler;

import brave.Tag;
import brave.internal.codec.JsonWriter;
import brave.internal.codec.WriteBuffer;
import brave.internal.codec.ZipkinV2JsonWriter;
import java.util.List;

/** Similar to {@code zipkin2.MutableSpan.SpanBytesEncoder} except no Zipkin dependency. */
public abstract class MutableSpanBytesEncoder {

  /**
   * Encodes a {@linkplain MutableSpan} into Zipkin's V2 json format.
   *
   * @param errorTag sets the tag for a {@linkplain MutableSpan#error()}, if the corresponding key
   *                 doesn't already exist.
   */
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
