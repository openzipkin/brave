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
    return tracer.currentSpanCustomizer().name(name);
  }

  /** {@inheritDoc} */
  @Override public SpanCustomizer tag(String key, String value) {
    return tracer.currentSpanCustomizer().tag(key, value);
  }

  /** {@inheritDoc} */
  @Override public SpanCustomizer annotate(String value) {
    return tracer.currentSpanCustomizer().annotate(value);
  }
}
