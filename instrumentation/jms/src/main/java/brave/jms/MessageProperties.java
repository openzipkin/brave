/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
