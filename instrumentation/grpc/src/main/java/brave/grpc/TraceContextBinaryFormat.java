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

import brave.grpc.GrpcPropagation.Tags;
import brave.internal.Nullable;
import brave.internal.Platform;
import brave.propagation.TraceContext;
import java.util.Collections;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This logs instead of throwing exceptions.
 *
 * <p>See
 * https://github.com/census-instrumentation/opencensus-specs/blob/master/encodings/BinaryEncoding.md
 */
final class TraceContextBinaryFormat {
  static final byte VERSION = 0,
    TRACE_ID_FIELD_ID = 0,
    SPAN_ID_FIELD_ID = 1,
    TRACE_OPTION_FIELD_ID = 2;

  static final int FORMAT_LENGTH =
    4 /* version + 3 fields */ + 16 /* trace ID */ + 8 /* span ID */ + 1 /* sampled bit */;

  static byte[] toBytes(TraceContext traceContext) {
    checkNotNull(traceContext, "traceContext");
    byte[] bytes = new byte[FORMAT_LENGTH];
    bytes[0] = VERSION;
    bytes[1] = TRACE_ID_FIELD_ID;
    writeLong(bytes, 2, traceContext.traceIdHigh());
    writeLong(bytes, 10, traceContext.traceId());
    bytes[18] = SPAN_ID_FIELD_ID;
    writeLong(bytes, 19, traceContext.spanId());
    bytes[27] = TRACE_OPTION_FIELD_ID;
    if (traceContext.sampled() != null && traceContext.sampled()) {
      bytes[28] = 1;
    }
    return bytes;
  }

  @Nullable static TraceContext parseBytes(byte[] bytes, @Nullable Tags tags) {
    if (bytes == null) throw new NullPointerException("bytes == null"); // programming error
    if (bytes.length == 0) return null;
    if (bytes[0] != VERSION) {
      Platform.get().log("Invalid input: unsupported version {0}", bytes[0], null);
      return null;
    }
    if (bytes.length < FORMAT_LENGTH - 2 /* sampled field + bit is optional */) {
      Platform.get().log("Invalid input: truncated", null);
      return null;
    }
    long traceIdHigh, traceId, spanId;
    int pos = 1;
    if (bytes[pos] == TRACE_ID_FIELD_ID) {
      pos++;
      traceIdHigh = readLong(bytes, pos);
      traceId = readLong(bytes, pos + 8);
      pos += 16;
    } else {
      Platform.get().log("Invalid input: expected trace ID at offset {0}", pos, null);
      return null;
    }
    if (bytes[pos] == SPAN_ID_FIELD_ID) {
      pos++;
      spanId = readLong(bytes, pos);
      pos += 8;
    } else {
      Platform.get().log("Invalid input: expected span ID at offset {0}", pos, null);
      return null;
    }
    // The trace options field is optional. However, when present, it should be valid.
    Boolean sampled = null;
    if (bytes.length > pos && bytes[pos] == TRACE_OPTION_FIELD_ID) {
      pos++;
      if (bytes.length < pos + 1) {
        Platform.get().log("Invalid input: truncated", null);
        return null;
      }
      sampled = bytes[pos] == 1;
    }
    TraceContext.Builder builder = TraceContext.newBuilder()
      .traceIdHigh(traceIdHigh)
      .traceId(traceId)
      .spanId(spanId);
    if (sampled != null) builder.sampled(sampled.booleanValue());
    if (tags != null) builder.extra(Collections.singletonList(tags));
    return builder.build();
  }

  /** Inspired by {@code okio.Buffer.writeLong} */
  static void writeLong(byte[] data, int pos, long v) {
    data[pos + 0] = (byte) ((v >>> 56L) & 0xff);
    data[pos + 1] = (byte) ((v >>> 48L) & 0xff);
    data[pos + 2] = (byte) ((v >>> 40L) & 0xff);
    data[pos + 3] = (byte) ((v >>> 32L) & 0xff);
    data[pos + 4] = (byte) ((v >>> 24L) & 0xff);
    data[pos + 5] = (byte) ((v >>> 16L) & 0xff);
    data[pos + 6] = (byte) ((v >>> 8L) & 0xff);
    data[pos + 7] = (byte) (v & 0xff);
  }

  /** Inspired by {@code okio.Buffer.readLong} */
  static long readLong(byte[] data, int pos) {
    return (data[pos] & 0xffL) << 56
      | (data[pos + 1] & 0xffL) << 48
      | (data[pos + 2] & 0xffL) << 40
      | (data[pos + 3] & 0xffL) << 32
      | (data[pos + 4] & 0xffL) << 24
      | (data[pos + 5] & 0xffL) << 16
      | (data[pos + 6] & 0xffL) << 8
      | (data[pos + 7] & 0xffL);
  }
}
