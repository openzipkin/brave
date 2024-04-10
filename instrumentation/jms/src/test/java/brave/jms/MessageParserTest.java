/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jms;

import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Topic;
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
