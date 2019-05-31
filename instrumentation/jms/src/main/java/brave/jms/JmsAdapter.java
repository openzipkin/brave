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

import brave.messaging.MessagingAdapter;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Topic;

abstract class JmsAdapter<T> extends MessagingAdapter<Destination, T, T> {
  final String remoteServiceName;

  JmsAdapter(JmsTracing jmsTracing) {
    remoteServiceName = jmsTracing.remoteServiceName;
  }

  static final class MessageAdapter extends JmsAdapter<Message> {
    MessageAdapter(JmsTracing jmsTracing) {
      super(jmsTracing);
    }

    @Override public String correlationId(Message message) {
      try {
        return message.getJMSMessageID();
      } catch (JMSException ignored) {
        // don't crash on wonky exceptions!
      }
      return null;
    }
  }

  @Override public T carrier(T message) {
    return message;
  }

  @Override public String channel(Destination channel) {
    try {
      if (channel instanceof Queue) {
        return ((Queue) channel).getQueueName();
      } else if (channel instanceof Topic) {
        return ((Topic) channel).getTopicName();
      }
      // TODO: we could use toString here..
    } catch (JMSException ignored) {
      // don't crash on wonky exceptions!
    }
    return null;
  }

  @Override public String channelKind(Destination channel) {
    if (channel instanceof Queue) {
      return "queue";
    } else if (channel instanceof Topic) {
      return "topic";
    }
    return null;
  }

  @Override public String messageKey(T message) {
    return null;
  }

  @Override public String brokerName(Destination channel) {
    return remoteServiceName;
  }
}
