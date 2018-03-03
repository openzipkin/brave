package brave.spring.rabbit;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext.Injector;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * MessagePostProcessor to be used with the {@link RabbitTemplate#setBeforePublishPostProcessors
 * RabbitTemplate's before publish post processors}, adding tracing functionality that creates a
 * {@link Span.Kind#PRODUCER} span.
 */
final class TracingMessagePostProcessor implements MessagePostProcessor {

  static final Setter<MessageProperties, String> SETTER = new Setter<MessageProperties, String>() {
    @Override public void put(MessageProperties carrier, String key, String value) {
      carrier.setHeader(key, value);
    }

    @Override public String toString() {
      return "MessageProperties::setHeader";
    }
  };

  final Injector<MessageProperties> injector;
  final Tracer tracer;

  TracingMessagePostProcessor(Tracing tracing) {
    this.injector = tracing.propagation().injector(SETTER);
    this.tracer = tracing.tracer();
  }

  @Override public Message postProcessMessage(Message message) {
    Span span = tracer.nextSpan().kind(Span.Kind.PRODUCER).start();
    injector.inject(span.context(), message.getMessageProperties());
    span.finish();
    return message;
  }
}
