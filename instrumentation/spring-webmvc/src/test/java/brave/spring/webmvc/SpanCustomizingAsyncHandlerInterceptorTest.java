package brave.spring.webmvc;

public class SpanCustomizingAsyncHandlerInterceptorTest
    extends SpanCustomizingHandlerInterceptorTest {

  public SpanCustomizingAsyncHandlerInterceptorTest() {
    super(new SpanCustomizingAsyncHandlerInterceptor());
    ((SpanCustomizingAsyncHandlerInterceptor) interceptor).handlerParser = parser;
  }
}
