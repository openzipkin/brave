package brave;

// Preferred to a constant NOOP in SpanCustomizer as the latter ends up in a hierachy including Span
enum NoopSpanCustomizer implements SpanCustomizer {
  INSTANCE;

  @Override public SpanCustomizer name(String name) {
    return this;
  }

  @Override public SpanCustomizer tag(String key, String value) {
    return this;
  }

  @Override public SpanCustomizer annotate(String value) {
    return this;
  }

  @Override public SpanCustomizer annotate(long timestamp, String value) {
    return this;
  }
}
