package brave;

public interface ErrorHandler {
  ErrorHandler NOOP = new NoopErrorHandler();

  void handleError(SpanCustomizer customizer, Throwable throwable);

}
