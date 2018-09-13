package brave.jms;

import java.io.Serializable;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.jms.CompletionListener;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.Message;
import javax.jms.TextMessage;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.junit.After;
import org.junit.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;
import static org.assertj.core.api.Assertions.assertThat;

public class PropertyFilterTest {

  @After public void clear() {
    PropertyFilter.MESSAGE_PROPERTIES_BUFFER.remove();
  }

  @Test public void filterProperties_message_empty() {
    TextMessage message = new ActiveMQTextMessage();

    PropertyFilter.MESSAGE.filterProperties(message, Collections.singleton("b3"));
  }

  @Test public void filterProperties_message_allTypes() throws Exception {
    TextMessage message = newMessageWithAllTypes();
    message.setStringProperty("b3", "00f067aa0ba902b7-00f067aa0ba902b7-1");

    PropertyFilter.MESSAGE.filterProperties(message, Collections.singleton("b3"));

    assertThat(message).isEqualToIgnoringGivenFields(newMessageWithAllTypes(), "processAsExpired");
  }

  static TextMessage newMessageWithAllTypes() throws Exception {
    TextMessage message = new ActiveMQTextMessage();
    setAllPropertyTypes(message);
    return message;
  }

  static void setAllPropertyTypes(TextMessage message) throws JMSException {
    message.setBooleanProperty("boolean", true);
    message.setByteProperty("byte", Byte.MAX_VALUE);
    message.setDoubleProperty("double", Double.MIN_VALUE);
    message.setFloatProperty("float", Float.MIN_VALUE);
    message.setIntProperty("int", Integer.MIN_VALUE);
    message.setLongProperty("long", Long.MIN_VALUE);
    message.setObjectProperty("object", Collections.emptyMap());
    message.setShortProperty("short", Short.MIN_VALUE);
    message.setStringProperty("string", "string");
  }

  @Test public void filterProperties_message_doesntPreventClassUnloading() {
    assertRunIsUnloadable(FilterMessage.class, getClass().getClassLoader());
  }

  static class FilterMessage implements Runnable {
    @Override public void run() {
      ActiveMQTextMessage message = new ActiveMQTextMessage();
      try {
        message.setStringProperty("b3", "00f067aa0ba902b7-00f067aa0ba902b7-1");
        message.setIntProperty("one", 1);
      } catch (JMSException e) {
        throw new AssertionError(e);
      }

      PropertyFilter.MESSAGE.filterProperties(message, Collections.singleton("b3"));

      try {
        assertThat(message.propertyExists("b3")).isFalse();
        assertThat(message.getIntProperty("one")).isEqualTo(1);
      } catch (JMSException e) {
        throw new AssertionError(e);
      }
    }
  }

  @Test public void filterProperties_producer_empty() {
    FakeJMSProducer producer = new FakeJMSProducer();

    PropertyFilter.JMS_PRODUCER.filterProperties(producer, Collections.singleton("b3"));
  }

  @Test public void filterProperties_producer_allTypes() throws Exception {
    FakeJMSProducer producer = new FakeJMSProducer();
    setAllPropertyTypes(producer.message);
    producer.setProperty("b3", "00f067aa0ba902b7-00f067aa0ba902b7-1");

    PropertyFilter.JMS_PRODUCER.filterProperties(producer, Collections.singleton("b3"));

    assertThat(producer.message)
        .isEqualToIgnoringGivenFields(newMessageWithAllTypes(), "processAsExpired");
  }

  @Test public void filterProperties_producer_doesntPreventClassUnloading() {
    assertRunIsUnloadable(FilterProducer.class, getClass().getClassLoader());
  }

  static class FilterProducer implements Runnable {
    @Override public void run() {
      FakeJMSProducer producer = new FakeJMSProducer();
      producer.setProperty("b3", "00f067aa0ba902b7-00f067aa0ba902b7-1");
      producer.setProperty("one", 1);

      PropertyFilter.JMS_PRODUCER.filterProperties(producer, Collections.singleton("b3"));

      assertThat(producer.propertyExists("b3")).isFalse();
      assertThat(producer.getIntProperty("one")).isEqualTo(1);
    }
  }

  // ActiveMQJMSProducer is hard to instantiate, and due to object pooling leaks on the classloader
  static class FakeJMSProducer implements JMSProducer {
    ActiveMQTextMessage message = new ActiveMQTextMessage();

    @Override public JMSProducer send(Destination destination, Message message) {
      return null;
    }

    @Override public JMSProducer send(Destination destination, String body) {
      return null;
    }

    @Override public JMSProducer send(Destination destination, Map<String, Object> body) {
      return null;
    }

    @Override public JMSProducer send(Destination destination, byte[] body) {
      return null;
    }

    @Override public JMSProducer send(Destination destination, Serializable body) {
      return null;
    }

    @Override public JMSProducer setDisableMessageID(boolean value) {
      return null;
    }

    @Override public boolean getDisableMessageID() {
      return false;
    }

    @Override public JMSProducer setDisableMessageTimestamp(boolean value) {
      return null;
    }

    @Override public boolean getDisableMessageTimestamp() {
      return false;
    }

    @Override public JMSProducer setDeliveryMode(int deliveryMode) {
      return null;
    }

    @Override public int getDeliveryMode() {
      return 0;
    }

    @Override public JMSProducer setPriority(int priority) {
      return null;
    }

    @Override public int getPriority() {
      return 0;
    }

    @Override public JMSProducer setTimeToLive(long timeToLive) {
      return null;
    }

    @Override public long getTimeToLive() {
      return 0;
    }

    @Override public JMSProducer setDeliveryDelay(long deliveryDelay) {
      return null;
    }

    @Override public long getDeliveryDelay() {
      return 0;
    }

    @Override public JMSProducer setAsync(CompletionListener completionListener) {
      return null;
    }

    @Override public CompletionListener getAsync() {
      return null;
    }

    @Override
    public JMSProducer setProperty(String name, boolean value) {
      try {
        message.setBooleanProperty(name, value);
      } catch (JMSException e) {
        throw new AssertionError(e);
      }
      return this;
    }

    @Override
    public JMSProducer setProperty(String name, byte value) {
      try {
        message.setByteProperty(name, value);
      } catch (JMSException e) {
        throw new AssertionError(e);
      }
      return this;
    }

    @Override
    public JMSProducer setProperty(String name, short value) {
      try {
        message.setShortProperty(name, value);
      } catch (JMSException e) {
        throw new AssertionError(e);
      }
      return this;
    }

    @Override
    public JMSProducer setProperty(String name, int value) {
      try {
        message.setIntProperty(name, value);
      } catch (JMSException e) {
        throw new AssertionError(e);
      }
      return this;
    }

    @Override
    public JMSProducer setProperty(String name, long value) {
      try {
        message.setLongProperty(name, value);
      } catch (JMSException e) {
        throw new AssertionError(e);
      }
      return this;
    }

    @Override
    public JMSProducer setProperty(String name, float value) {
      try {
        message.setFloatProperty(name, value);
      } catch (JMSException e) {
        throw new AssertionError(e);
      }
      return this;
    }

    @Override
    public JMSProducer setProperty(String name, double value) {
      try {
        message.setDoubleProperty(name, value);
      } catch (JMSException e) {
        throw new AssertionError(e);
      }
      return this;
    }

    @Override
    public JMSProducer setProperty(String name, String value) {
      try {
        message.setStringProperty(name, value);
      } catch (JMSException e) {
        throw new AssertionError(e);
      }
      return this;
    }

    @Override
    public JMSProducer setProperty(String name, Object value) {
      try {
        message.setObjectProperty(name, value);
      } catch (JMSException e) {
        throw new AssertionError(e);
      }
      return this;
    }

    @Override
    public JMSProducer clearProperties() {
      message.clearProperties();
      return this;
    }

    @Override
    public boolean propertyExists(String name) {
      try {
        return message.propertyExists(name);
      } catch (JMSException e) {
        throw new AssertionError(e);
      }
    }

    @Override
    public boolean getBooleanProperty(String name) {
      try {
        return message.getBooleanProperty(name);
      } catch (JMSException e) {
        throw new AssertionError(e);
      }
    }

    @Override
    public byte getByteProperty(String name) {
      try {
        return message.getByteProperty(name);
      } catch (JMSException e) {
        throw new AssertionError(e);
      }
    }

    @Override
    public short getShortProperty(String name) {
      try {
        return message.getShortProperty(name);
      } catch (JMSException e) {
        throw new AssertionError(e);
      }
    }

    @Override
    public int getIntProperty(String name) {
      try {
        return message.getIntProperty(name);
      } catch (JMSException e) {
        throw new AssertionError(e);
      }
    }

    @Override
    public long getLongProperty(String name) {
      try {
        return message.getLongProperty(name);
      } catch (JMSException e) {
        throw new AssertionError(e);
      }
    }

    @Override
    public float getFloatProperty(String name) {
      try {
        return message.getFloatProperty(name);
      } catch (JMSException e) {
        throw new AssertionError(e);
      }
    }

    @Override
    public double getDoubleProperty(String name) {
      try {
        return message.getDoubleProperty(name);
      } catch (JMSException e) {
        throw new AssertionError(e);
      }
    }

    @Override
    public String getStringProperty(String name) {
      try {
        return message.getStringProperty(name);
      } catch (JMSException e) {
        throw new AssertionError(e);
      }
    }

    @Override
    public Object getObjectProperty(String name) {
      try {
        return message.getObjectProperty(name);
      } catch (JMSException e) {
        throw new AssertionError(e);
      }
    }

    @Override
    public Set<String> getPropertyNames() {
      Set<String> result = new LinkedHashSet<>();
      try {
        Enumeration e = message.getPropertyNames();
        while (e.hasMoreElements()) {
          result.add(e.nextElement().toString());
        }
      } catch (JMSException e) {
        throw new AssertionError(e);
      }
      return result;
    }

    @Override public JMSProducer setJMSCorrelationIDAsBytes(byte[] correlationID) {
      return null;
    }

    @Override public byte[] getJMSCorrelationIDAsBytes() {
      return new byte[0];
    }

    @Override public JMSProducer setJMSCorrelationID(String correlationID) {
      return null;
    }

    @Override public String getJMSCorrelationID() {
      return null;
    }

    @Override public JMSProducer setJMSType(String type) {
      return null;
    }

    @Override public String getJMSType() {
      return null;
    }

    @Override public JMSProducer setJMSReplyTo(Destination replyTo) {
      return null;
    }

    @Override public Destination getJMSReplyTo() {
      return null;
    }
  }
}
