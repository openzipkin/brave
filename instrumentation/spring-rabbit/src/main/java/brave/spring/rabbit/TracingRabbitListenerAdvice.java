package brave.spring.rabbit;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.Tracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

/**
 * TracingRabbitListenerAdvice is an AOP advice to be used with the {@link
 * SimpleMessageListenerContainer#setAdviceChain(Advice...) SimpleMessageListenerContainer} in order
 * to add tracing functionality to a spring-amqp managed message consumer. While a majority of
 * brave's instrumentation points is implemented using a delegating wrapper approach, this extension
 * point was ideal as it covers both programmatic and @RabbitListener annotation driven amqp consumers.
 */
public class TracingRabbitListenerAdvice implements MethodInterceptor {

  private static final Propagation.Getter<MessageProperties, String> GETTER =
      new Propagation.Getter<MessageProperties, String>() {

        @Override public String get(MessageProperties carrier, String key) {
          return (String) carrier.getHeaders().get(key);
        }
      };
  private final Extractor<MessageProperties> extractor;
  private final Tracer tracer;

  public TracingRabbitListenerAdvice(Tracing tracing) {
    this.extractor = tracing.propagation().extractor(GETTER);
    this.tracer = tracing.tracer();
  }

  @Override public Object invoke(MethodInvocation methodInvocation) throws Throwable {

    final Message message = (Message) methodInvocation.getArguments()[1];
    final TraceContextOrSamplingFlags extracted = extractor.extract(message.getMessageProperties());
    final Span span = tracer.nextSpan(extracted).kind(Span.Kind.CONSUMER).start();
    try (SpanInScope ws = tracer.withSpanInScope(span)) {
      return methodInvocation.proceed();
    } catch (Throwable t) {
      tagErrorSpan(span, t);
      throw t;
    } finally {
      span.finish();
    }
  }

  private void tagErrorSpan(Span span, Throwable t) {
    String errorMessage = t.getMessage();
    if (errorMessage == null) errorMessage = t.getClass().getSimpleName();
    span.tag("error", errorMessage);
  }
}
