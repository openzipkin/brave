/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brave.kafka.streams;

import brave.propagation.Propagation.Getter;
import java.nio.charset.Charset;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

final class KafkaStreamsPropagation {

  static final Charset UTF_8 = Charset.forName("UTF-8");

  static final Getter<Headers, String> GETTER = (carrier, key) -> {
    Header header = carrier.lastHeader(key);
    if (header == null) return null;
    return new String(header.value(), UTF_8);
  };

  KafkaStreamsPropagation() {
  }
}
