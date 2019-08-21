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

import org.junit.Test;
import org.junit.rules.TestName;

/** When adding tests here, also add to {@linkplain brave.jms.ITTracingJMSConsumer} */
public class ITJms_2_0_TracingMessageConsumer extends ITJms_1_1_TracingMessageConsumer {
  @Override JmsTestRule newJmsTestRule(TestName testName) {
    return new ArtemisJmsTestRule(testName);
  }

  // Inability to encode "b3" on a received BytesMessage only applies to ActiveMQ 5.x
  @Test public void receive_resumesTrace_bytes() throws Exception {
    receive_resumesTrace(() -> messageProducer.send(bytesMessage), messageConsumer);
  }
}
