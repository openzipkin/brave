package brave.spring.rabbit;

import brave.propagation.Propagation.Getter;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Injector;
import org.springframework.amqp.core.MessageProperties;

import static brave.propagation.B3SingleFormat.writeB3SingleFormat;
import static brave.propagation.B3SingleFormat.writeB3SingleFormatWithoutParentId;

final class SpringRabbitPropagation {
  static final TraceContext TEST_CONTEXT = TraceContext.newBuilder().traceId(1L).spanId(1L).build();
  static final MessageProperties B3_SINGLE_TEST_HEADERS = new MessageProperties();

  static {
    B3_SINGLE_TEST_HEADERS.setHeader("b3", writeB3SingleFormat(TEST_CONTEXT));
  }

  static final Injector<MessageProperties> B3_SINGLE_INJECTOR = new Injector<MessageProperties>() {
    @Override public void inject(TraceContext traceContext, MessageProperties carrier) {
      carrier.setHeader("b3", writeB3SingleFormatWithoutParentId(traceContext));
    }

    @Override public String toString() {
      return "MessageProperties::setHeader(\"b3\",singleHeaderFormatWithoutParent)";
    }
  };

  static final Setter<MessageProperties, String> SETTER = new Setter<MessageProperties, String>() {
    @Override public void put(MessageProperties carrier, String key, String value) {
      carrier.setHeader(key, value);
    }

    @Override public String toString() {
      return "MessageProperties::setHeader";
    }
  };

  static final Getter<MessageProperties, String> GETTER = new Getter<MessageProperties, String>() {
    @Override public String get(MessageProperties carrier, String key) {
      return (String) carrier.getHeaders().get(key);
    }

    @Override public String toString() {
      return "MessageProperties::setHeader";
    }
  };

  SpringRabbitPropagation() {
  }
}
