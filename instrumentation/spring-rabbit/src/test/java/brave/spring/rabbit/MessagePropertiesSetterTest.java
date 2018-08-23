package brave.spring.rabbit;

import brave.propagation.Propagation;
import brave.test.propagation.PropagationSetterTest;
import java.util.Collections;
import org.springframework.amqp.core.MessageProperties;

import static brave.spring.rabbit.SpringRabbitPropagation.GETTER;
import static brave.spring.rabbit.SpringRabbitPropagation.SETTER;

public class MessagePropertiesSetterTest extends PropagationSetterTest<MessageProperties, String> {
  MessageProperties carrier = new MessageProperties();

  @Override public Propagation.KeyFactory<String> keyFactory() {
    return Propagation.KeyFactory.STRING;
  }

  @Override protected MessageProperties carrier() {
    return carrier;
  }

  @Override protected Propagation.Setter<MessageProperties, String> setter() {
    return SETTER;
  }

  @Override protected Iterable<String> read(MessageProperties carrier, String key) {
    String result = GETTER.get(carrier, key);
    return result == null ? Collections.emptyList() : Collections.singleton(result);
  }
}
