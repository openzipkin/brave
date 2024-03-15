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
import brave.Tracer.SpanInScope;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.Collections;
import java.util.List;

class TracingMessageListenerOrderly implements MessageListenerOrderly {
  private final RocketMQTracing tracing;
  final MessageListenerOrderly messageListenerOrderly;

  TracingMessageListenerOrderly(RocketMQTracing tracing, MessageListenerOrderly messageListenerOrderly) {
    this.tracing = tracing;
    this.messageListenerOrderly = messageListenerOrderly;
  }

  @Override
  public final ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs,
                                                   ConsumeOrderlyContext context) {
    for (MessageExt msg : msgs) {
      TracingConsumerRequest request = new TracingConsumerRequest(msg);
      Span span =
        Util.createAndStartSpan(tracing, tracing.consumerExtractor, tracing.consumerSampler,
          request, msg.getProperties());
      span.name(RocketMQTags.FROM_PREFIX + msg.getTopic());

      ConsumeOrderlyStatus result;
      try (SpanInScope scope = tracing.tracer.withSpanInScope(span)) {
        result = messageListenerOrderly.consumeMessage(Collections.singletonList(msg), context);
      } catch (Throwable e) {
        span.error(e);
        throw e;
      } finally {
        span.finish();
      }

      if (result != ConsumeOrderlyStatus.SUCCESS) {
        return result;
      }
    }

    return ConsumeOrderlyStatus.SUCCESS;
  }
}
