package brave.internal.recorder;

import brave.internal.Nullable;
import brave.internal.recorder.MutableSpan.AnnotationConsumer;
import brave.internal.recorder.MutableSpan.TagConsumer;
import zipkin2.Endpoint;
import zipkin2.Span;

// internal until we figure out how the api should sit.
final class MutableSpanConverter
    implements TagConsumer<Span.Builder>, AnnotationConsumer<Span.Builder> {

  final String localServiceName;
  @Nullable final String localIp;
  final int localPort;
  final Endpoint localEndpoint;

  MutableSpanConverter(String localServiceName, String localIp, int localPort) {
    if (localServiceName == null) throw new NullPointerException("localServiceName == null");
    this.localServiceName = localServiceName;
    this.localIp = localIp;
    this.localPort = localPort;
    this.localEndpoint =
        Endpoint.newBuilder().serviceName(localServiceName).ip(localIp).port(localPort).build();
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

    addLocalEndpoint(span.localServiceName, span.localIp, span.localPort, result);
    String remoteServiceName = span.remoteServiceName(), remoteIp = span.remoteIp();
    if (remoteServiceName != null || remoteIp != null) {
      result.remoteEndpoint(zipkin2.Endpoint.newBuilder()
          .serviceName(remoteServiceName)
          .ip(remoteIp)
          .port(span.remotePort())
          .build());
    }
    span.forEachTag(this, result);
    span.forEachAnnotation(this, result);
    if (span.shared()) result.shared(true);
  }

  // avoid re-allocating an endpoint when we have the same data
  void addLocalEndpoint(String serviceName, @Nullable String ip, int port, Span.Builder span) {
    if (serviceName == null) serviceName = localServiceName;
    if (ip == null) ip = localIp;
    if (port <= 0) port = localPort;
    if (localServiceName.equals(serviceName)
        && (localIp == null ? ip == null : localIp.equals(ip))
        && localPort == port) {
      span.localEndpoint(localEndpoint);
    } else {
      span.localEndpoint(Endpoint.newBuilder().serviceName(serviceName).ip(ip).port(port).build());
    }
  }

  @Override public void accept(Span.Builder target, String key, String value) {
    target.putTag(key, value);
  }

  @Override public void accept(Span.Builder target, long timestamp, String value) {
    target.addAnnotation(timestamp, value);
  }
}
