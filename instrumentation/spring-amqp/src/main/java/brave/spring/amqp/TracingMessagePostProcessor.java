package brave.spring.amqp;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext.Injector;

public class TracingMessagePostProcessor implements MessagePostProcessor {

  private static final Propagation.Setter<MessageProperties, String> SETTER = new Propagation.Setter<MessageProperties, String>() {

    @Override public void put(MessageProperties carrier, String key, String value) {
      carrier.setHeader(key, value);
    }
  };

  private final Injector<MessageProperties> injector;
  private final Tracer tracer;

  public TracingMessagePostProcessor(Tracing tracing) {
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
