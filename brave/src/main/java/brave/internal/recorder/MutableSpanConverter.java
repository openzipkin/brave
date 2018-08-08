package brave.internal.recorder;

import zipkin2.Span;

// internal until we figure out how the api should sit.
public final class MutableSpanConverter {
  static final MutableSpan.TagConsumer TAG_CONSUMER = new MutableSpan.TagConsumer<Span.Builder>() {
    @Override public void accept(Span.Builder target, String key, String value) {
      target.putTag(key, value);
    }
  };

  static final MutableSpan.AnnotationConsumer ANNOTATION_CONSUMER =
      new MutableSpan.AnnotationConsumer<Span.Builder>() {
        @Override public void accept(Span.Builder target, long timestamp, String value) {
          target.addAnnotation(timestamp, value);
        }
      };

  public static void convert(MutableSpan span, Span.Builder result) {
    result.name(span.name());

    long start = span.startTimestamp(), finish = span.finishTimestamp();
    result.timestamp(start);
    if (start != 0 && finish != 0L) result.duration(Math.max(finish - start, 1));

    // use ordinal comparison to defend against version skew
    brave.Span.Kind kind = span.kind();
    if (kind != null && kind.ordinal() < Span.Kind.values().length) {
      result.kind(Span.Kind.values()[kind.ordinal()]);
    }

    String remoteServiceName = span.remoteServiceName(), remoteIp = span.remoteIp();
    if (remoteServiceName != null || remoteIp != null) {
      result.remoteEndpoint(zipkin2.Endpoint.newBuilder()
          .serviceName(remoteServiceName)
          .ip(remoteIp)
          .port(span.remotePort())
          .build());
    }
    span.forEachTag(TAG_CONSUMER, result);
    span.forEachAnnotation(ANNOTATION_CONSUMER, result);
    if (span.shared()) result.shared(true);
  }
}
