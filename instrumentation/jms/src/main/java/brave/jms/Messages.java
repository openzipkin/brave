package brave.jms;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Set;
import javax.jms.JMSException;
import javax.jms.Message;

final class Messages {

  /**
   * This implies copying properties because the JMS spec says you can't write properties until
   * {@link Message#clearProperties()} has been called.
   *
   * <p> See https://docs.oracle.com/javaee/6/api/javax/jms/Message.html
   */
  static void filterProperties(Message message, Set<String> namesToClear) throws JMSException {
    ArrayList<Object> retainedProperties = messagePropertiesBuffer();
    try {
      doFilterProperties(message, namesToClear, retainedProperties);
    } finally {
      retainedProperties.clear(); // ensure no object references are held due to JMS exceptions
    }
  }

  static void doFilterProperties(
      Message message, Set<String> namesToClear, ArrayList<Object> retainedProperties
  ) throws JMSException {
    Enumeration<?> names = message.getPropertyNames();
    while (names.hasMoreElements()) {
      String name = (String) names.nextElement();
      Object value = message.getObjectProperty(name);
      if (!namesToClear.contains(name) && value != null) {
        retainedProperties.add(name);
        retainedProperties.add(value);
      }
    }

    // redo the properties to keep
    message.clearProperties();
    for (int i = 0, length = retainedProperties.size(); i < length; i += 2) {
      message.setObjectProperty(
          retainedProperties.get(i).toString(),
          retainedProperties.get(i + 1)
      );
    }
  }

  static final ThreadLocal<ArrayList<Object>> MESSAGE_PROPERTIES_BUFFER = new ThreadLocal<>();

  /** Also use pair indexing for temporary message properties: (name, value). */
  static ArrayList<Object> messagePropertiesBuffer() {
    ArrayList<Object> messagePropertiesBuffer = MESSAGE_PROPERTIES_BUFFER.get();
    if (messagePropertiesBuffer == null) {
      messagePropertiesBuffer = new ArrayList<>();
      MESSAGE_PROPERTIES_BUFFER.set(messagePropertiesBuffer);
    }
    return messagePropertiesBuffer;
  }
}
