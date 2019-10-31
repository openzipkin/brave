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

import brave.internal.Nullable;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Topic;

import static brave.internal.Throwables.propagateIfFatal;
import static brave.jms.JmsTracing.log;

// Utility class until full parsing support is added
final class MessageParser {

  @Nullable static Destination destination(Message message) {
    try {
      return message.getJMSDestination();
    } catch (Throwable t) {
      propagateIfFatal(t);
      log(t, "error getting destination of message {0}", message, null);
    }
    return null;
  }

  @Nullable static String channelKind(@Nullable Destination destination) {
    if (destination instanceof Queue) return "queue";
    if (destination instanceof Topic) return "topic";
    return null;
  }

  @Nullable static String channelName(@Nullable Destination destination) {
    try {
      if (destination instanceof Queue) {
        return ((Queue) destination).getQueueName();
      } else if (destination instanceof Topic) {
        return ((Topic) destination).getTopicName();
      }
    } catch (Throwable t) {
      propagateIfFatal(t);
      log(t, "error getting destination name from {0}", destination, null);
    }
    return null;
  }
}
