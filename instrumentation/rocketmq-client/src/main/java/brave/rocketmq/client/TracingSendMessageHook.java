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
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;

final class TracingSendMessageHook implements SendMessageHook {
  final RocketMQTracing tracing;

  public TracingSendMessageHook(RocketMQTracing tracing) {
    this.tracing = tracing;
  }

  @Override
  public String hookName() {
    return "TracingSendMessageHook";
  }

  @Override
  public void sendMessageBefore(SendMessageContext context) {
    if (context == null || context.getMessage() == null) {
      return;
    }

    Message msg = context.getMessage();
    TracingProducerRequest request = new TracingProducerRequest(msg);
    Span span =
      Util.createAndStartSpan(tracing, tracing.producerExtractor, tracing.producerSampler,
        request,
        msg.getProperties());
    span.name(RocketMQTags.TO_PREFIX + msg.getTopic());
    if (msg.getTags() != null && !msg.getTags().isEmpty()) {
      span.tag(RocketMQTags.ROCKETMQ_TAGS, msg.getTags());
    }
    context.setMqTraceContext(span);
    tracing.producerInjector.inject(span.context(), request);
  }

  @Override
  public void sendMessageAfter(SendMessageContext context) {
    if (context == null || context.getMessage() == null || context.getMqTraceContext() == null) {
      return;
    }

    SendResult sendResult = context.getSendResult();
    Span span = (Span) context.getMqTraceContext();
    TracingProducerRequest request = new TracingProducerRequest(context.getMessage());

    long timestamp = tracing.tracing.clock(span.context()).currentTimeMicroseconds();
    if (sendResult == null) {
      if (context.getCommunicationMode() == CommunicationMode.ASYNC) {
        return;
      }
      span.finish(timestamp);
      tracing.producerInjector.inject(span.context(), request);
      return;
    }

    tracing.producerInjector.inject(span.context(), request);
    span.finish(timestamp);
  }
}
