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

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import java.util.List;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;

// TODO: I think we don't want to expose a custom class rather wrap in context and prove a user can
// do custom tagging via their own MessageListenerConcurrently.
// Maybe expose RocketMQTracing.messageListenerConcurrently() to wrap theirs or make spans default
// and not expose this.
public abstract class TracingMessageListenerConcurrently implements MessageListenerConcurrently {

  private final int delayLevelWhenNextConsume;

  private final RocketMQTracing tracing;

  public TracingMessageListenerConcurrently(int delayLevelWhenNextConsume,
    RocketMQTracing tracing) {
    this.delayLevelWhenNextConsume = delayLevelWhenNextConsume;
    this.tracing = tracing;
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
        result = handleMessage(msg, context);
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

  protected abstract ConsumeConcurrentlyStatus handleMessage(MessageExt messageExt,
    ConsumeConcurrentlyContext context);
}
