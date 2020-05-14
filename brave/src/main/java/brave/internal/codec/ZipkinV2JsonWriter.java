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

import brave.Tag;
import brave.handler.MutableSpan;
import brave.internal.Nullable;

import static brave.internal.codec.JsonEscaper.jsonEscape;
import static brave.internal.codec.JsonEscaper.jsonEscapedSizeInBytes;
import static brave.internal.codec.WriteBuffer.asciiSizeInBytes;

// @Immutable
public final class ZipkinV2JsonWriter implements WriteBuffer.Writer<MutableSpan> {
  final Tag<Throwable> errorTag;

  public ZipkinV2JsonWriter(Tag<Throwable> errorTag) {
    if (errorTag == null) throw new NullPointerException("errorTag == null");
    this.errorTag = errorTag;
  }

  @Override public int sizeInBytes(MutableSpan span) {
    int sizeInBytes = 1; // {
    if (span.traceId() != null) {
      sizeInBytes += 12; // "traceId":""
      sizeInBytes += span.traceId().length();
    }
    if (span.parentId() != null) {
      if (sizeInBytes > 1) sizeInBytes++; // ,
      sizeInBytes += 29; // "parentId":"0123456789abcdef"
    }
    if (span.id() != null) {
      if (sizeInBytes > 1) sizeInBytes++; // ,
      sizeInBytes += 23; // "id":"0123456789abcdef"
    }
    if (span.kind() != null) {
      if (sizeInBytes > 1) sizeInBytes++; // ,
      sizeInBytes += 9; // "kind":""
      sizeInBytes += span.kind().name().length();
    }
    if (span.name() != null) {
      if (sizeInBytes > 1) sizeInBytes++; // ,
      sizeInBytes += 9; // "name":""
      sizeInBytes += jsonEscapedSizeInBytes(span.name());
    }
    if (span.startTimestamp() != 0L) {
      if (sizeInBytes > 1) sizeInBytes++; // ,
      sizeInBytes += 12; // "timestamp":
      sizeInBytes += asciiSizeInBytes(span.startTimestamp());
      if (span.finishTimestamp() != 0L) {
        sizeInBytes += 12; // ,"duration":
        sizeInBytes += asciiSizeInBytes(span.finishTimestamp() - span.startTimestamp());
      }
    }
    int localEndpointSizeInBytes =
        endpointSizeInBytes(span.localServiceName(), span.localIp(), span.localPort());
    if (localEndpointSizeInBytes > 0) {
      if (sizeInBytes > 1) sizeInBytes++; // ,
      sizeInBytes += (16 + localEndpointSizeInBytes); // "localEndpoint":
    }
    int remoteEndpointSizeInBytes =
        endpointSizeInBytes(span.remoteServiceName(), span.remoteIp(), span.remotePort());
    if (remoteEndpointSizeInBytes > 0) {
      if (sizeInBytes > 1) sizeInBytes++; // ,
      sizeInBytes += (17 + remoteEndpointSizeInBytes); // "remoteEndpoint":
    }
    int annotationCount = span.annotationCount();
    if (annotationCount > 0) {
      if (sizeInBytes > 1) sizeInBytes++; // ,
      sizeInBytes += 16; // "annotations":[]
      if (annotationCount > 1) sizeInBytes += annotationCount - 1; // comma to join elements
      for (int i = 0; i < annotationCount; i++) {
        long timestamp = span.annotationTimestampAt(i);
        String value = span.annotationValueAt(i);
        sizeInBytes += annotationSizeInBytes(timestamp, value);
      }
    }
    int tagCount = span.tagCount();
    String errorValue = errorTag.value(span.error(), null);
    if (tagCount > 0 || errorValue != null) {
      if (sizeInBytes > 1) sizeInBytes++; // ,
      sizeInBytes += 9; // "tags":{}
      boolean foundError = false;
      for (int i = 0; i < tagCount; i++) {
        String key = span.tagKeyAt(i);
        if (!foundError && key.equals("error")) foundError = true;
        String value = span.tagValueAt(i);
        sizeInBytes += tagSizeInBytes(key, value);
      }
      if (errorValue != null && !foundError) {
        tagCount++;
        sizeInBytes += tagSizeInBytes(errorTag.key(), errorValue);
      }
      if (tagCount > 1) sizeInBytes += tagCount - 1; // comma to join elements
    }
    if (Boolean.TRUE.equals(span.debug())) {
      if (sizeInBytes > 1) sizeInBytes++; // ,
      sizeInBytes += 12; // "debug":true
    }
    if (Boolean.TRUE.equals(span.shared())) {
      if (sizeInBytes > 1) sizeInBytes++; // ,
      sizeInBytes += 13; // "shared":true
    }
    return sizeInBytes + 1; // }
  }

  @Override public void write(MutableSpan span, WriteBuffer b) {
    b.writeByte('{');
    boolean wroteField = false;
    if (span.traceId() != null) {
      wroteField = writeFieldBegin(b, "traceId", wroteField);
      b.writeByte('"');
      b.writeAscii(span.traceId());
      b.writeByte('"');
    }
    if (span.parentId() != null) {
      wroteField = writeFieldBegin(b, "parentId", wroteField);
      b.writeByte('"');
      b.writeAscii(span.parentId());
      b.writeByte('"');
    }
    if (span.id() != null) {
      wroteField = writeFieldBegin(b, "id", wroteField);
      b.writeByte('"');
      b.writeAscii(span.id());
      b.writeByte('"');
    }
    if (span.kind() != null) {
      wroteField = writeFieldBegin(b, "kind", wroteField);
      b.writeByte('"');
      b.writeAscii(span.kind().toString());
      b.writeByte('"');
    }
    if (span.name() != null) {
      wroteField = writeFieldBegin(b, "name", wroteField);
      b.writeByte('"');
      jsonEscape(span.name(), b);
      b.writeByte('"');
    }
    long startTimestamp = span.startTimestamp(), finishTimestamp = span.finishTimestamp();
    if (startTimestamp != 0L) {
      wroteField = writeFieldBegin(b, "timestamp", wroteField);
      b.writeAscii(startTimestamp);
      if (finishTimestamp != 0L) {
        wroteField = writeFieldBegin(b, "duration", wroteField);
        b.writeAscii(finishTimestamp - startTimestamp);
      }
    }
    if (span.localServiceName() != null || span.localIp() != null) {
      wroteField = writeFieldBegin(b, "localEndpoint", wroteField);
      writeEndpoint(b, span.localServiceName(), span.localIp(), span.localPort());
    }
    if (span.remoteServiceName() != null || span.remoteIp() != null) {
      wroteField = writeFieldBegin(b, "remoteEndpoint", wroteField);
      writeEndpoint(b, span.remoteServiceName(), span.remoteIp(), span.remotePort());
    }
    int annotationLength = span.annotationCount();
    if (annotationLength > 0) {
      wroteField = writeFieldBegin(b, "annotations", wroteField);
      b.writeByte('[');
      for (int i = 0; i < annotationLength; ) {
        long timestamp = span.annotationTimestampAt(i);
        String value = span.annotationValueAt(i);
        writeAnnotation(timestamp, value, b);
        if (++i < annotationLength) b.writeByte(',');
      }
      b.writeByte(']');
    }
    int tagCount = span.tagCount();
    String errorValue = errorTag.value(span.error(), null);
    if (tagCount > 0 || errorValue != null) {
      wroteField = writeFieldBegin(b, "tags", wroteField);
      b.writeByte('{');
      boolean foundError = false;
      for (int i = 0; i < tagCount; ) {
        String key = span.tagKeyAt(i);
        if (!foundError && key.equals("error")) foundError = true;
        String value = span.tagValueAt(i);
        writeKeyValue(b, key, value);
        if (++i < tagCount) b.writeByte(',');
      }
      if (errorValue != null && !foundError) {
        if (tagCount > 0) b.writeByte(',');
        writeKeyValue(b, errorTag.key(), errorValue);
      }
      b.writeByte('}');
    }
    if (Boolean.TRUE.equals(span.debug())) {
      wroteField = writeFieldBegin(b, "debug", wroteField);
      b.writeAscii("true");
    }
    if (Boolean.TRUE.equals(span.shared())) {
      writeFieldBegin(b, "shared", wroteField);
      b.writeAscii("true");
    }
    b.writeByte('}');
  }

  static int endpointSizeInBytes(@Nullable String serviceName, @Nullable String ip, int port) {
    int sizeInBytes = 0;
    if (serviceName != null) {
      sizeInBytes += 16; // "serviceName":""
      sizeInBytes += jsonEscapedSizeInBytes(serviceName);
    }
    if (ip != null) {
      if (sizeInBytes > 0) sizeInBytes++; // ,
      sizeInBytes += 9; // "ipv4":"" or "ipv6":""
      sizeInBytes += ip.length();
      if (port != 0) {
        if (sizeInBytes != 1) sizeInBytes++; // ,
        sizeInBytes += 7; // "port":
        sizeInBytes += asciiSizeInBytes(port);
      }
    }
    return sizeInBytes == 0 ? 0 : sizeInBytes + 2; // {}
  }

  static int annotationSizeInBytes(long timestamp, String value) {
    int sizeInBytes = 25; // {"timestamp":,"value":""}
    sizeInBytes += asciiSizeInBytes(timestamp);
    sizeInBytes += jsonEscapedSizeInBytes(value);
    return sizeInBytes;
  }

  static int tagSizeInBytes(String key, String value) {
    int sizeInBytes = 5; // "":""
    sizeInBytes += jsonEscapedSizeInBytes(key);
    sizeInBytes += jsonEscapedSizeInBytes(value);
    return sizeInBytes;
  }

  boolean writeFieldBegin(WriteBuffer b, String fieldName, boolean wroteField) {
    if (wroteField) b.writeByte(',');
    wroteField = true;

    b.writeByte('"');
    b.writeAscii(fieldName);
    b.writeByte('"');
    b.writeByte(':');
    return wroteField;
  }

  static void writeEndpoint(WriteBuffer b,
      @Nullable String serviceName, @Nullable String ip, int port) {
    b.writeByte('{');
    boolean wroteField = false;
    if (serviceName != null) {
      b.writeAscii("\"serviceName\":\"");
      JsonEscaper.jsonEscape(serviceName, b);
      b.writeByte('"');
      wroteField = true;
    }
    if (ip != null) {
      if (wroteField) b.writeByte(',');
      if (IpLiteral.detectFamily(ip) == IpLiteral.IpFamily.IPv4) {
        b.writeAscii("\"ipv4\":\"");
      } else {
        b.writeAscii("\"ipv6\":\"");
      }
      b.writeAscii(ip);
      b.writeByte('"');
      wroteField = true;
    }
    if (port != 0) {
      if (wroteField) b.writeByte(',');
      b.writeAscii("\"port\":");
      b.writeAscii(port);
    }
    b.writeByte('}');
  }

  static void writeAnnotation(long timestamp, String value, WriteBuffer b) {
    b.writeAscii("{\"timestamp\":");
    b.writeAscii(timestamp);
    b.writeAscii(",\"value\":\"");
    jsonEscape(value, b);
    b.writeByte('"');
    b.writeByte('}');
  }

  static void writeKeyValue(WriteBuffer b, String key, String value) {
    b.writeByte('"');
    JsonEscaper.jsonEscape(key, b);
    b.writeAscii("\":\"");
    JsonEscaper.jsonEscape(value, b);
    b.writeByte('"');
  }
}
