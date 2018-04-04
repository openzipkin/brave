package brave;

/** This is a simplified type used for parsing errors. It only allows annotations or tags. */
// This implementation allows a future type ScopedSpan which will not support backdated annotations
public class ErrorParser {
  /** Adds no tags to the span representing the operation in error. */
  public static final ErrorParser NOOP = new ErrorParser() {
    @Override protected void error(Throwable error, Object customizer) {
    }
  };

  /** Used to parse errors on a subtype of {@linkplain SpanCustomizer} */
  public final void error(Throwable error, SpanCustomizer customizer) {
    error(error, (Object) customizer);
  }

  /**
   * Override to change what data from the error are parsed into the span modeling it. By
   * default, this tags "error" as the message or simple name of the type.
   */
  protected void error(Throwable error, Object span) {
    String message = error.getMessage();
    if (message == null) message = error.getClass().getSimpleName();
    tag(span, "error", message);
  }

  /** Same behaviour as {@link brave.SpanCustomizer#annotate(String)} */
  protected final void annotate(Object span, String value) {
    if (span instanceof SpanCustomizer) {
      ((SpanCustomizer) span).annotate(value);
    }
  }

  /** Same behaviour as {@link brave.SpanCustomizer#tag(String, String)} */
  protected final void tag(Object span, String key, String message) {
    if (span instanceof SpanCustomizer) {
      ((SpanCustomizer) span).tag(key, message);
    }
  }
}
