/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rocketmq.client;

import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.List;
import java.util.function.BiFunction;

/**
 * RocketMQ Consumer decorator for orderly message processing with tracing support.
 */
final class TracingMessageListenerOrderly extends AbstractMessageListener implements MessageListenerOrderly {
  final MessageListenerOrderly messageListenerOrderly;
  final BiFunction<ConsumeOrderlyStatus, ConsumeOrderlyStatus, Boolean> ORDERLY_CHECK =
      (result, success) -> result == success;

  TracingMessageListenerOrderly(
      RocketMQTracing rocketMQTracing,
      MessageListenerOrderly messageListenerOrderly
  ) {
    super(rocketMQTracing);
    this.messageListenerOrderly = messageListenerOrderly;
  }

  @Override
  public ConsumeOrderlyStatus consumeMessage(
      final List<MessageExt> msgs,
      final ConsumeOrderlyContext context
  ) {
    return processConsumeMessage(
        msgs,
        list -> messageListenerOrderly.consumeMessage(list, context),
        ORDERLY_CHECK,
        ConsumeOrderlyStatus.SUCCESS
    );
  }
}
