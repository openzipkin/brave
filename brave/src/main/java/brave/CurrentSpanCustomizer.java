package brave;

/**
 * Provides a mechanism for end users to be able to customise the current span.
 *
 * <p>Handles the case of there being no current span in scope.
 */
public final class CurrentSpanCustomizer implements SpanCustomizer {

  private final Tracer tracer;

  /** Creates a span customizer that will affect the current span in scope if present */
  public static CurrentSpanCustomizer create(Tracing tracing) {
    return new CurrentSpanCustomizer(tracing);
  }

  CurrentSpanCustomizer(Tracing tracing) {
    this.tracer = tracing.tracer();
  }

  /** {@inheritDoc} */
  @Override public SpanCustomizer name(String name) {
    Span currentSpan = tracer.currentSpan();
    if (currentSpan != null) {
      currentSpan.name(name);
    }
    return this;
  }

  /** {@inheritDoc} */
  @Override public SpanCustomizer tag(String key, String value) {
    Span currentSpan = tracer.currentSpan();
    if (currentSpan != null) {
      currentSpan.tag(key, value);
    }
    return this;
  }

  /** {@inheritDoc} */
  @Override public SpanCustomizer annotate(String value) {
    Span currentSpan = tracer.currentSpan();
    if (currentSpan != null) {
      currentSpan.annotate(value);
    }
    return this;
  }

  /** {@inheritDoc} */
  @Override public SpanCustomizer annotate(long timestamp, String value) {
    Span currentSpan = tracer.currentSpan();
    if (currentSpan != null) {
      currentSpan.annotate(timestamp, value);
    }
    return this;
  }
}
