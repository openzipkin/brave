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
import jakarta.jms.Queue;
import jakarta.jms.Topic;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MessageParserTest {

  // Ex: https://docs.oracle.com/cd/B19306_01/server.102/b14291/oracle/jms/AQjmsDestination.html
  interface QueueAndTopic extends Queue, Topic {
  }

  @Test void channelKind_queueAndTopic_null() {
    assertThat(MessageParser.channelKind(null)).isNull();
  }

  @Test void channelKind_queueAndTopic_queueOnQueueName() throws JMSException {
    QueueAndTopic destination = mock(QueueAndTopic.class);
    when(destination.getQueueName()).thenReturn("queue-foo");

    assertThat(MessageParser.channelKind(destination))
      .isEqualTo("queue");
  }

  @Test void channelKind_queueAndTopic_topicOnNoQueueName() throws JMSException {
    QueueAndTopic destination = mock(QueueAndTopic.class);
    when(destination.getTopicName()).thenReturn("topic-foo");

    assertThat(MessageParser.channelKind(destination))
      .isEqualTo("topic");
  }

  @Test void channelName_queueAndTopic_null() {
    assertThat(MessageParser.channelName(null)).isNull();
  }

  @Test void channelName_queueAndTopic_queueOnQueueName() throws JMSException {
    QueueAndTopic destination = mock(QueueAndTopic.class);
    when(destination.getQueueName()).thenReturn("queue-foo");

    assertThat(MessageParser.channelName(destination))
      .isEqualTo("queue-foo");
  }

  @Test void channelName_queueAndTopic_topicOnNoQueueName() throws JMSException {
    QueueAndTopic destination = mock(QueueAndTopic.class);
    when(destination.getTopicName()).thenReturn("topic-foo");

    assertThat(MessageParser.channelName(destination))
      .isEqualTo("topic-foo");
  }
}
