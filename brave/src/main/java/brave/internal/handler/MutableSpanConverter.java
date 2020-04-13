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
package brave.internal.handler;

import brave.handler.MutableSpan;
import brave.handler.MutableSpan.AnnotationConsumer;
import brave.handler.MutableSpan.TagConsumer;
import brave.internal.Nullable;
import java.util.Locale;
import zipkin2.Endpoint;
import zipkin2.Span;

// internal until we figure out how the api should sit.
public final class MutableSpanConverter {
  final Endpoint defaultEndpoint;
  final int defaultEndpointHashCode;

  public MutableSpanConverter(MutableSpan defaultSpan) {
    // non-Zipkin models allow mixed case service names, but Zipkin does not.
    String serviceName = defaultSpan.localServiceName();
    if (serviceName != null) serviceName = serviceName.toLowerCase(Locale.ROOT);
    String ip = defaultSpan.localIp();
    int port = defaultSpan.localPort();
    this.defaultEndpointHashCode = hashEndpointParameters(serviceName, ip, port);
    this.defaultEndpoint = Endpoint.newBuilder().serviceName(serviceName).ip(ip).port(port).build();
  }

  /** Used to avoid re-allocating the default endpoint. */
  static int hashEndpointParameters(@Nullable String serviceName, @Nullable String ip, int port) {
    int h = 1;
    h *= 1000003;
    h ^= (serviceName == null) ? 0 : serviceName.hashCode();
    h *= 1000003;
    h ^= (ip == null) ? 0 : ip.hashCode();
    h *= 1000003;
    h ^= port;
    return h;
  }

  void convert(MutableSpan span, Span.Builder result) {
    result.name(span.name());

    long start = span.startTimestamp(), finish = span.finishTimestamp();
    result.timestamp(start);
    if (start != 0 && finish != 0L) result.duration(Math.max(finish - start, 1));

    // use ordinal comparison to defend against version skew
    brave.Span.Kind kind = span.kind();
    if (kind != null && kind.ordinal() < Span.Kind.values().length) {
      result.kind(Span.Kind.values()[kind.ordinal()]);
    }

    String localServiceName = span.localServiceName(), localIp = span.localIp();
    if (localServiceName != null || localIp != null) {
      addLocalEndpoint(localServiceName, localIp, span.localPort(), result);
    }

    String remoteServiceName = span.remoteServiceName(), remoteIp = span.remoteIp();
    if (remoteServiceName != null || remoteIp != null) {
      result.remoteEndpoint(zipkin2.Endpoint.newBuilder()
        .serviceName(remoteServiceName)
        .ip(remoteIp)
        .port(span.remotePort())
        .build());
    }

    span.forEachTag(Consumer.INSTANCE, result);
    span.forEachAnnotation(Consumer.INSTANCE, result);
    if (span.shared()) result.shared(true);
    if (span.debug()) result.debug(true);
  }

  // avoid re-allocating an endpoint when we have the same data
  void addLocalEndpoint(@Nullable String serviceName, @Nullable String ip, int port,
    Span.Builder span) {
    if (serviceName != null) serviceName = serviceName.toLowerCase(Locale.ROOT);
    if (hashEndpointParameters(serviceName, ip, port) == defaultEndpointHashCode) {
      span.localEndpoint(defaultEndpoint);
    } else {
      span.localEndpoint(Endpoint.newBuilder().serviceName(serviceName).ip(ip).port(port).build());
    }
  }

  enum Consumer implements TagConsumer<Span.Builder>, AnnotationConsumer<Span.Builder> {
    INSTANCE;

    @Override public void accept(Span.Builder target, String key, String value) {
      target.putTag(key, value);
    }

    @Override public void accept(Span.Builder target, long timestamp, String value) {
      target.addAnnotation(timestamp, value);
    }
  }
}
