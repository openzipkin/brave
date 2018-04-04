package brave.spring.rabbit;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.Tracing;
import brave.internal.Nullable;
import brave.propagation.Propagation.Getter;
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
import zipkin2.Endpoint;

import static brave.Span.Kind.CONSUMER;
import static brave.spring.rabbit.SpringRabbitTracing.RABBIT_EXCHANGE;
import static brave.spring.rabbit.SpringRabbitTracing.RABBIT_QUEUE;
import static brave.spring.rabbit.SpringRabbitTracing.RABBIT_ROUTING_KEY;

/**
 * TracingRabbitListenerAdvice is an AOP advice to be used with the {@link
 * SimpleMessageListenerContainer#setAdviceChain(Advice...) SimpleMessageListenerContainer} in order
 * to add tracing functionality to a spring-rabbit managed message consumer. While a majority of
 * brave's instrumentation points is implemented using a delegating wrapper approach, this extension
 * point was ideal as it covers both programmatic and {@link RabbitListener} annotation driven amqp
 * consumers.
 *
 * The spans are modeled as a zero duration CONSUMER span to represent the consuming the message
 * from the rabbit broker, and a child span representing the processing of the messsage.
 */
final class TracingRabbitListenerAdvice implements MethodInterceptor {

  static final Getter<MessageProperties, String> GETTER = new Getter<MessageProperties, String>() {
    @Override public String get(MessageProperties carrier, String key) {
      return (String) carrier.getHeaders().get(key);
    }

    @Override public String toString() {
      return "MessageProperties::setHeader";
    }
  };

  final Extractor<MessageProperties> extractor;
  final Tracer tracer;
  final Tracing tracing;
  @Nullable final String remoteServiceName;

  TracingRabbitListenerAdvice(Tracing tracing, @Nullable String remoteServiceName) {
    this.extractor = tracing.propagation().extractor(GETTER);
    this.tracer = tracing.tracer();
    this.tracing = tracing;
    this.remoteServiceName = remoteServiceName;
  }

  /**
   * MethodInterceptor for {@link SimpleMessageListenerContainer.ContainerDelegate#invokeListener(Channel, Message)}
   */
  @Override public Object invoke(MethodInvocation methodInvocation) throws Throwable {
    Message message = (Message) methodInvocation.getArguments()[1];
    TraceContextOrSamplingFlags extracted = extractTraceContextAndRemoveHeaders(message);

    // named for BlockingQueueConsumer.nextMessage, which we can't currently see
    Span consumerSpan = tracer.nextSpan(extracted).kind(CONSUMER).name("next-message");
    Span listenerSpan = tracer.newChild(consumerSpan.context()).name("on-message");

    if (!consumerSpan.isNoop()) {
      consumerSpan.start();
      tagReceivedMessageProperties(consumerSpan, message.getMessageProperties());
      if (remoteServiceName != null) {
        consumerSpan.remoteEndpoint(Endpoint.newBuilder().serviceName(remoteServiceName).build());
      }
      consumerSpan.finish();
      listenerSpan.start();
    }

    try (SpanInScope ws = tracer.withSpanInScope(listenerSpan)) {
      return methodInvocation.proceed();
    } catch (Throwable t) {
      listenerSpan.error(t);
      throw t;
    } finally {
      listenerSpan.finish();
    }
  }

  TraceContextOrSamplingFlags extractTraceContextAndRemoveHeaders(Message message) {
    MessageProperties messageProperties = message.getMessageProperties();
    TraceContextOrSamplingFlags extracted = extractor.extract(messageProperties);
    Map<String, Object> headers = messageProperties.getHeaders();
    for (String key : tracing.propagation().keys()) {
      headers.remove(key);
    }
    return extracted;
  }

  void tagReceivedMessageProperties(Span span, MessageProperties messageProperties) {
    maybeTag(span, RABBIT_EXCHANGE, messageProperties.getReceivedExchange());
    maybeTag(span, RABBIT_ROUTING_KEY, messageProperties.getReceivedRoutingKey());
    maybeTag(span, RABBIT_QUEUE, messageProperties.getConsumerQueue());
  }

  static void maybeTag(Span span, String tag, String value) {
    if (value != null) span.tag(tag, value);
  }
}
