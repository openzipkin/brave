package brave;

public class ErrorParser {
  /** Adds no tags to the span representing the operation in error. */
  public static final ErrorParser NOOP = new ErrorParser() {
    @Override public void error(Throwable error, SpanCustomizer customizer) {
    }
  };

  /**
   * Override to change what data from the error are parsed into the span modeling it. By
   * default, this tags "error" as the message or simple name of the type.
   */
  public void error(Throwable error, SpanCustomizer customizer) {
    String message = error.getMessage();
    if (message == null) message = error.getClass().getSimpleName();
    customizer.tag("error", message);
  }
}
