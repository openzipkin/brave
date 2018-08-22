package brave.jms;

import org.junit.rules.TestName;

public class ITJms_2_0_TracingMessageConsumer extends ITJms_1_1_TracingMessageConsumer {
  @Override JmsTestRule newJmsTestRule(TestName testName) {
    return new ArtemisJmsTestRule(testName);
  }
}
