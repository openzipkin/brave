package brave.spring.rabbit;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext.Injector;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * MessagePostProcessor to be used with the {@link RabbitTemplate#setBeforePublishPostProcessors(MessagePostProcessor...)
 * RabbitTemplate's before publish post processors}, adding tracing functionality that creates a
 * {@link Span.Kind#PRODUCER} span.
 *
 * To integrate, instantiate this class and set on a {@link RabbitTemplate#setBeforePublishPostProcessors(MessagePostProcessor...)}
 *
 */
class TracingMessagePostProcessor implements MessagePostProcessor {

  private static final Propagation.Setter<MessageProperties, String> SETTER =
      new Propagation.Setter<MessageProperties, String>() {

        @Override public void put(MessageProperties carrier, String key, String value) {
          carrier.setHeader(key, value);
        }
      };

  private final Injector<MessageProperties> injector;
  private final Tracer tracer;

  TracingMessagePostProcessor(Tracing tracing) {
    this.injector = tracing.propagation().injector(SETTER);
    this.tracer = tracing.tracer();
  }

  @Override public Message postProcessMessage(Message message) throws AmqpException {

    final Span span = tracer.nextSpan().kind(Span.Kind.PRODUCER).start();
    injector.inject(span.context(), message.getMessageProperties());
    span.finish();
    return message;
  }
}
