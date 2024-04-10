/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.kafka.clients;

import brave.internal.Nullable;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import static brave.kafka.clients.KafkaTracing.log;
import static java.nio.charset.StandardCharsets.UTF_8;

final class KafkaHeaders {
  static void replaceHeader(Headers headers, String key, String value) {
    try {
      headers.remove(key);
      headers.add(key, value.getBytes(UTF_8));
    } catch (IllegalStateException e) {
      log(e, "error setting header {0} in headers {1}", key, headers);
    }
  }

  @Nullable static String lastStringHeader(Headers headers, String key) {
    Header header = headers.lastHeader(key);
    if (header == null || header.value() == null) return null;
    return new String(header.value(), UTF_8);
  }

  KafkaHeaders() {
  }
}
