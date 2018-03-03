package brave.spring.rabbit;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.Tracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import com.rabbitmq.client.Channel;
import java.util.Map;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
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
  private static final String RABBIT_EXCHANGE = "rabbit.exchange";
  private static final String RABBIT_ROUTING_KEY = "rabbit.routing.key";
  private static final String RABBIT_QUEUE = "rabbit.queue";

  private final Extractor<MessageProperties> extractor;
  private final Tracer tracer;
  private final Tracing tracing;

  TracingRabbitListenerAdvice(Tracing tracing) {
    this.extractor = tracing.propagation().extractor(GETTER);
    this.tracer = tracing.tracer();
    this.tracing = tracing;
  }

  /**
   * MethodInterceptor for {@link SimpleMessageListenerContainer.ContainerDelegate#invokeListener(Channel, Message)}
   */
  @Override public Object invoke(MethodInvocation methodInvocation) throws Throwable {
    final Message message = (Message) methodInvocation.getArguments()[1];
    final TraceContextOrSamplingFlags extracted = extractTraceContextAndRemoveHeaders(message);

    final Span consumerSpan = tracer.nextSpan(extracted).kind(CONSUMER).start();
    tagReceivedMessageProperties(consumerSpan, message.getMessageProperties());
    String consumerQueue = message.getMessageProperties().getConsumerQueue();
    if (consumerQueue != null) consumerSpan.name(consumerQueue);
    consumerSpan.finish();

    final Span listenerSpan = tracer.newChild(consumerSpan.context()).name("on-message").start();
    try (SpanInScope ws = tracer.withSpanInScope(listenerSpan)) {
      return methodInvocation.proceed();
    } catch (Throwable t) {
      tagErrorSpan(listenerSpan, t);
      throw t;
    } finally {
      listenerSpan.finish();
    }
  }

  private TraceContextOrSamplingFlags extractTraceContextAndRemoveHeaders(Message message) {
    final MessageProperties messageProperties = message.getMessageProperties();
    final TraceContextOrSamplingFlags extracted = extractor.extract(messageProperties);
    final Map<String, Object> headers = messageProperties.getHeaders();
    for (String key : tracing.propagation().keys()) {
      headers.remove(key);
    }
    return extracted;
  }

  private void tagReceivedMessageProperties(Span span, MessageProperties messageProperties) {
    tag(span, RABBIT_EXCHANGE, messageProperties.getReceivedExchange());
    tag(span, RABBIT_ROUTING_KEY, messageProperties.getReceivedRoutingKey());
    tag(span, RABBIT_QUEUE, messageProperties.getConsumerQueue());
  }

  private void tag(Span span, String tag, String value) {
    if (value != null) span.tag(tag, value);
  }

  private void tagErrorSpan(Span span, Throwable t) {
    String errorMessage = t.getMessage();
    if (errorMessage == null) errorMessage = t.getClass().getSimpleName();
    span.tag("error", errorMessage);
  }
}
