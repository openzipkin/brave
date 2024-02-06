/*
 * Copyright 2013-2024 The OpenZipkin Authors
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
package brave.rocketmq.client;

import java.util.Collections;
import java.util.List;

import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;

import brave.Span;
import brave.Tracer.SpanInScope;

class TracingMessageListenerConcurrently implements MessageListenerConcurrently {

  private final int delayLevelWhenNextConsume;
  private final RocketMQTracing tracing;
  final MessageListenerConcurrently messageListenerConcurrently;

  TracingMessageListenerConcurrently(int delayLevelWhenNextConsume,
                                     RocketMQTracing tracing, MessageListenerConcurrently messageListenerConcurrently) {
    this.delayLevelWhenNextConsume = delayLevelWhenNextConsume;
    this.tracing = tracing;
    this.messageListenerConcurrently = messageListenerConcurrently;
  }

  @Override
  public final ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs,
    ConsumeConcurrentlyContext context) {
    for (MessageExt msg : msgs) {
      TracingConsumerRequest request = new TracingConsumerRequest(msg);
      Span span =
        Util.createAndStartSpan(tracing, tracing.consumerExtractor, tracing.consumerSampler,
          request, msg.getProperties());
      span.name(RocketMQTags.FROM_PREFIX + msg.getTopic());

      ConsumeConcurrentlyStatus result;
      try (SpanInScope scope = tracing.tracer().withSpanInScope(span)) {
        result = messageListenerConcurrently.consumeMessage(Collections.singletonList(msg), context);
      } catch (Exception e) {
        context.setDelayLevelWhenNextConsume(delayLevelWhenNextConsume);
        result = ConsumeConcurrentlyStatus.RECONSUME_LATER;
      } finally {
        long timestamp = tracing.tracing.clock(span.context()).currentTimeMicroseconds();
        span.finish(timestamp);
      }

      if (result != ConsumeConcurrentlyStatus.CONSUME_SUCCESS) {
        return result;
      }
    }

    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
  }
}
