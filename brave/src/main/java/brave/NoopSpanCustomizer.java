package brave;

/**
 * Performs no operations as the span represented by this is not sampled to report to the tracing
 * system.
 */
// Preferred to a constant NOOP in SpanCustomizer as the latter ends up in a hierachy including Span
public enum NoopSpanCustomizer implements SpanCustomizer {
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
}
