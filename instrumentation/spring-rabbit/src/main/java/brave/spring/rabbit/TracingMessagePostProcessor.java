package brave.spring.rabbit;

import brave.Span;
import brave.Tracing;
import brave.propagation.Propagation;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;

public class TracingMessagePostProcessor implements MessagePostProcessor {

    private final Tracing tracing;

    public TracingMessagePostProcessor(Tracing tracing) {
        this.tracing = tracing;
    }

    @Override
    public Message postProcessMessage(Message message) throws AmqpException {
        Span span = tracing.tracer().nextSpan();

        injectTraceHeaders(message, span);

        span.kind(Span.Kind.PRODUCER)
                .start()
                .finish();
        return message;
    }

    private void injectTraceHeaders(Message message, Span span) {
        MessageProperties messageProperties = message.getMessageProperties();
        tracing.propagation()
                .injector(new TracingSetter())
                .inject(span.context(), messageProperties);
    }

    static class TracingSetter implements Propagation.Setter<MessageProperties, String> {
        @Override
        public void put(MessageProperties carrier, String key, String value) {
            carrier.setHeader(key, value);
        }
    }
}
