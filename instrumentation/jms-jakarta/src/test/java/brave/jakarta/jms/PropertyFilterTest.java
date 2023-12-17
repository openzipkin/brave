/*
 * Copyright 2013-2023 The OpenZipkin Authors
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
package brave.jakarta.jms;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import java.util.Collections;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyByte;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PropertyFilterTest {
  static ClientSession clientSession = mock(ClientSession.class);

  @BeforeEach void setup() {
    when(clientSession.createMessage(anyByte(), eq(true), eq(0L), anyLong(), eq((byte) 4)))
      .thenReturn(new ClientMessageImpl());
  }

  @AfterEach void clear() {
    PropertyFilter.MESSAGE_PROPERTIES_BUFFER.remove();
  }

  @Test void filterProperties_message_empty() {
    TextMessage message = new ActiveMQTextMessage(clientSession);

    PropertyFilter.filterProperties(message, Collections.singleton("b3"));
  }

  @Test void filterProperties_message_allTypes() throws JMSException {
    TextMessage message = newMessageWithAllTypes();
    message.setStringProperty("b3", "00f067aa0ba902b7-00f067aa0ba902b7-1");

    PropertyFilter.filterProperties(message, Collections.singleton("b3"));

    assertThat(message).isEqualToIgnoringGivenFields(newMessageWithAllTypes(), "processAsExpired");
  }

  // When brave-instrumentation-jms is wrapped around an AWS SQSConnectionFactory, PropertyFilter.filterProperties()
  // attempts to re-set properties on the received SQSMessage object. Doing so fails because SQSMessage throws an
  // IllegalArgumentException if either the property name or value are empty. (Even though the properties came from
  // SQSMessage to begin with.) Verify the IllegalArgumentException does not escape its try/catch block resulting in the
  // message silently failing to process.
  //
  // https://github.com/awslabs/amazon-sqs-java-messaging-lib/blob/b462bdceac814c56e75ee0ba638b3928ce8adee1/src/main/java/com/amazon/sqs/javamessaging/message/SQSMessage.java#L904-L909
  @Test void filterProperties_message_handlesOnSetException() throws JMSException {
    Message message = mock(Message.class);
    when(message.getPropertyNames()).thenReturn(
      Collections.enumeration(Collections.singletonList("JMS_SQS_DeduplicationId")));
    when(message.getObjectProperty("JMS_SQS_DeduplicationId")).thenReturn("");
    doThrow(new IllegalArgumentException()).when(message).setObjectProperty(anyString(), eq(""));

    assertThatCode(() -> PropertyFilter.filterProperties(message,
      Collections.singleton("b3"))).doesNotThrowAnyException();
  }

  @Test void filterProperties_message_passesFatalOnSetException() throws JMSException {
    Message message = mock(Message.class);
    when(message.getPropertyNames()).thenReturn(
      Collections.enumeration(Collections.singletonList("JMS_SQS_DeduplicationId")));
    when(message.getObjectProperty("JMS_SQS_DeduplicationId")).thenReturn("");
    doThrow(new LinkageError()).when(message).setObjectProperty(anyString(), eq(""));

    assertThatThrownBy(
      () -> PropertyFilter.filterProperties(message, Collections.singleton("b3"))).isInstanceOf(
      LinkageError.class);
  }

  static TextMessage newMessageWithAllTypes() throws JMSException {
    TextMessage message = new ActiveMQTextMessage(clientSession);
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
    message.setObjectProperty("object", new byte[] {});
    message.setShortProperty("short", Short.MIN_VALUE);
    message.setStringProperty("string", "string");
  }

  // TODO: something outside is causing this to be not unloadable. Move this to
  // an invoker test.
  @Disabled @Test void filterProperties_message_doesntPreventClassUnloading() {
    assertRunIsUnloadable(FilterMessage.class, getClass().getClassLoader());
  }

  static class FilterMessage implements Runnable {
    @Override public void run() {
      when(clientSession.createMessage(anyByte(), eq(true), eq(0L), anyLong(), eq((byte) 4)))
        .thenReturn(new ClientMessageImpl());

      ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
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
