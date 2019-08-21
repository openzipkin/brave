/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.jms;

import java.util.Collections;
import javax.jms.JMSException;
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

    PropertyFilter.filterProperties(message, Collections.singleton("b3"));
  }

  @Test public void filterProperties_message_allTypes() throws Exception {
    TextMessage message = newMessageWithAllTypes();
    message.setStringProperty("b3", "00f067aa0ba902b7-00f067aa0ba902b7-1");

    PropertyFilter.filterProperties(message, Collections.singleton("b3"));

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

      PropertyFilter.filterProperties(message, Collections.singleton("b3"));

      try {
        assertThat(message.propertyExists("b3")).isFalse();
        assertThat(message.getIntProperty("one")).isEqualTo(1);
      } catch (JMSException e) {
        throw new AssertionError(e);
      }
    }
  }
}
