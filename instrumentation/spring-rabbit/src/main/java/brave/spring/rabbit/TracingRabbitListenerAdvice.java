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
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

import static brave.Span.Kind.CONSUMER;
import static brave.Span.Kind.SERVER;

/**
 * TracingRabbitListenerAdvice is an AOP advice to be used with the {@link
 * SimpleMessageListenerContainer#setAdviceChain(Advice...) SimpleMessageListenerContainer} in order
 * to add tracing functionality to a spring-amqp managed message consumer. While a majority of
 * brave's instrumentation points is implemented using a delegating wrapper approach, this extension
 * point was ideal as it covers both programmatic and {@link RabbitListener} annotation driven amqp
 * consumers.
 *
 * The spans are modelled as a zero duration CONSUMER span to represent the consuming the message
 * from the rabbit broker, and a child SERVER span representing the processing of the messsage.
 */
class TracingRabbitListenerAdvice implements MethodInterceptor {

  private static final Propagation.Getter<MessageProperties, String> GETTER =
      new Propagation.Getter<MessageProperties, String>() {

        @Override public String get(MessageProperties carrier, String key) {
          return (String) carrier.getHeaders().get(key);
        }
      };
  private final Extractor<MessageProperties> extractor;
  private final Tracer tracer;

  TracingRabbitListenerAdvice(Tracing tracing) {
    this.extractor = tracing.propagation().extractor(GETTER);
    this.tracer = tracing.tracer();
  }

  @Override public Object invoke(MethodInvocation methodInvocation) throws Throwable {
    final Message message = (Message) methodInvocation.getArguments()[1];
    final TraceContextOrSamplingFlags extracted = extractor.extract(message.getMessageProperties());

    final Span consumerSpan = tracer.nextSpan(extracted).kind(CONSUMER).start();
    consumerSpan.finish();

    final Span serverSpan = tracer.newChild(consumerSpan.context()).kind(SERVER).start();
    try (SpanInScope ws = tracer.withSpanInScope(serverSpan)) {
      return methodInvocation.proceed();
    } catch (Throwable t) {
      tagErrorSpan(serverSpan, t);
      throw t;
    } finally {
      serverSpan.finish();
    }
  }

  private void tagErrorSpan(Span span, Throwable t) {
    String errorMessage = t.getMessage();
    if (errorMessage == null) errorMessage = t.getClass().getSimpleName();
    span.tag("error", errorMessage);
  }
}
