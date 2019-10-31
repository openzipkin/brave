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

import brave.propagation.Propagation.Getter;
import brave.propagation.Propagation.Setter;
import javax.jms.Message;

import static brave.internal.Throwables.propagateIfFatal;
import static brave.jms.JmsTracing.log;

final class MessagePropagation {
  static final Getter<Message, String> GETTER = new Getter<Message, String>() {
    @Override public String get(Message message, String name) {
      try {
        return message.getStringProperty(name);
      } catch (Throwable t) {
        propagateIfFatal(t);
        log(t, "error getting property {0} from message {1}", name, message);
        return null;
      }
    }

    @Override public String toString() {
      return "Message::getStringProperty";
    }
  };

  static final Setter<Message, String> SETTER = new Setter<Message, String>() {
    @Override public void put(Message message, String name, String value) {
      try {
        message.setStringProperty(name, value);
      } catch (Throwable t) {
        propagateIfFatal(t);
        log(t, "error setting property {0} on message {1}", name, message);
      }
    }

    @Override public String toString() {
      return "Message::setStringProperty";
    }
  };
}
