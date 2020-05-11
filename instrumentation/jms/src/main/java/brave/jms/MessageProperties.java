/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
import javax.jms.Message;

import static brave.internal.Throwables.propagateIfFatal;
import static brave.jms.JmsTracing.log;

class MessageProperties {
  /**
   * Same as {@link Message#getStringProperty(String)}, just doesn't throw or coerce non-strings to
   * strings.
   */
  @Nullable static String getPropertyIfString(Message message, String name) {
    try {
      Object o = message.getObjectProperty(name);
      if (o instanceof String) return o.toString();
      return null;
    } catch (Throwable t) {
      propagateIfFatal(t);
      log(t, "error getting property {0} from message {1}", name, message);
      return null;
    }
  }

  /** Same as {@link Message#setStringProperty(String, String)}, just doesn't throw. */
  static void setStringProperty(Message message, String name, String value) {
    try {
      message.setStringProperty(name, value);
    } catch (Throwable t) {
      propagateIfFatal(t);
      log(t, "error setting property {0} on message {1}", name, message);
    }
  }
}
