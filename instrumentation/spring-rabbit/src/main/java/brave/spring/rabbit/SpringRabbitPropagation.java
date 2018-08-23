package brave.spring.rabbit;

import brave.propagation.Propagation.Getter;
import brave.propagation.Propagation.Setter;
import org.springframework.amqp.core.MessageProperties;

final class SpringRabbitPropagation {

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
