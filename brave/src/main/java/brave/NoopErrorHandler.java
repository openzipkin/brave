package brave;

class NoopErrorHandler implements ErrorHandler {
  NoopErrorHandler() {
  }

  @Override
  public void handleError(SpanCustomizer customizer, Throwable throwable) {
  }
}
