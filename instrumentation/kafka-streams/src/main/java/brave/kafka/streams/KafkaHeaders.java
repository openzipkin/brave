/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.kafka.streams;

import brave.internal.Nullable;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import static java.nio.charset.StandardCharsets.UTF_8;

final class KafkaHeaders {
  static void replaceHeader(Headers headers, String key, String value) {
    headers.remove(key);
    headers.add(key, value.getBytes(UTF_8));
  }

  @Nullable static String lastStringHeader(Headers headers, String key) {
    Header header = headers.lastHeader(key);
    if (header == null || header.value() == null) return null;
    return new String(header.value(), UTF_8);
  }

  KafkaHeaders() {
  }
}
