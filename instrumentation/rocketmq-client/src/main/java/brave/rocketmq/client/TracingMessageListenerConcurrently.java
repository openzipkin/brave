/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rocketmq.client;

import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.List;
import java.util.function.BiFunction;

/**
 * RocketMQ Consumer decorator for concurrent message processing with tracing support.
 */
final class TracingMessageListenerConcurrently extends AbstractMessageListener implements MessageListenerConcurrently {
  final MessageListenerConcurrently messageListenerConcurrently;
  final BiFunction<ConsumeConcurrentlyStatus, ConsumeConcurrentlyStatus, Boolean> CONCURRENT_CHECK =
      (result, success) -> result == success;

  TracingMessageListenerConcurrently(
      RocketMQTracing rocketMQTracing,
      MessageListenerConcurrently messageListenerConcurrently
  ) {
    super(rocketMQTracing);
    this.messageListenerConcurrently = messageListenerConcurrently;
  }

  @Override
  public ConsumeConcurrentlyStatus consumeMessage(
      final List<MessageExt> msgs,
      final ConsumeConcurrentlyContext context
  ) {
    return processConsumeMessage(
        msgs,
        list -> messageListenerConcurrently.consumeMessage(list, context),
        CONCURRENT_CHECK,
        ConsumeConcurrentlyStatus.CONSUME_SUCCESS
    );
  }
}
