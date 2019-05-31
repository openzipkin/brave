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
package brave.messaging;

import brave.SpanCustomizer;
import brave.Tracing;
import brave.internal.Nullable;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.TraceContext;

/**
 * <p>Methods will not be invoked with a span in scope. Please use the explicit {@link
 * TraceContext} if you need to create tags based on propagated data like {@link
 * ExtraFieldPropagation}.
 */
// there are no producer/consumer subtypes because it is simpler than multiple inheritance when the
// same libary handles producer and consumer relationships.
public class MessagingParser {

  /**
   * Override to change what data to add to the span when a message operation starts. By default,
   * this sets the span name to the operation name the tag "messaging.channel" if available.
   *
   * <p>If you only want to change the span name, you can override {@link
   * #spanName(String, MessagingAdapter, Object, Object)} instead.
   *
   * @param msg null when a bulk operation
   * @see #spanName(String, MessagingAdapter, Object, Object)
   * @see #finish(String, MessagingAdapter, Object, Object, TraceContext, SpanCustomizer)
   */
  // Context is here so that people can decide to add tags based on local root etc.
  public <Chan, Msg, C> void start(String operation, MessagingAdapter<Chan, Msg, C> adapter,
    Chan channel, @Nullable Msg msg, TraceContext context, SpanCustomizer customizer) {
    customizer.name(spanName(operation, adapter, channel, msg));
    addMessageTags(adapter, channel, msg, context, customizer);
  }

  // channel is nullable as JMS could have an exception getting it from the message
  protected <Chan, Msg, C> void addMessageTags(MessagingAdapter<Chan, Msg, C> adapter,
    @Nullable Chan channel, @Nullable Msg msg, TraceContext context, SpanCustomizer customizer) {
    String channelName = adapter.channel(channel);
    if (channelName != null) customizer.tag("messaging.channel", channelName);
  }

  /**
   * Override to change what data to add to the span when a message operation completes.
   *
   * <p>This adds no tags by default. Error tagging is delegated to {@link Tracing#errorParser()}.
   *
   * @param msg null when a bulk operation
   * @see #start(String, MessagingAdapter, Object, Object, TraceContext, SpanCustomizer)
   */
  // Context is here so that people can add tags based on extra fields without the cost of a scoping
  // TODO: this call is probably too complex. We need to see what if any data is only available on
  // messaging callback which is not already present when you send or consume. If nothing is usable,
  // possibly it is better to remove this method or reduce it to not include (adapter, channel, msg)
  public <Chan, Msg, C> void finish(String operation, MessagingAdapter<Chan, Msg, C> adapter,
    Chan channel, @Nullable Msg msg, TraceContext context, SpanCustomizer customizer) {
  }

  /** Returns the span name of a message operation. Defaults to the operation name. */
  protected <Chan, Msg, C> String spanName(String operation,
    MessagingAdapter<Chan, Msg, C> adapter, Chan channel, @Nullable Msg msg) {
    return operation;
  }
}
