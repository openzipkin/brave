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
package brave.spring.rabbit;

import brave.propagation.Propagation.Getter;
import brave.propagation.Propagation.Setter;
import org.springframework.amqp.core.MessageProperties;

final class SpringRabbitPropagation {
  static final Setter<MessageProperties, String> SETTER = new Setter<MessageProperties, String>() {
    @Override public void put(MessageProperties properties, String key, String value) {
      properties.setHeader(key, value);
    }

    @Override public String toString() {
      return "MessageProperties::setHeader";
    }
  };

  static final Getter<MessageProperties, String> GETTER = new Getter<MessageProperties, String>() {
    @Override public String get(MessageProperties properties, String key) {
      return (String) properties.getHeaders().get(key);
    }

    @Override public String toString() {
      return "MessageProperties::setHeader";
    }
  };

  SpringRabbitPropagation() {
  }
}
