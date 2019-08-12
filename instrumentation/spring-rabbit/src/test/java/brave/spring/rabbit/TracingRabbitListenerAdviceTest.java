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
package brave.spring.rabbit;

import brave.Tracing;
import brave.propagation.ThreadLocalCurrentTraceContext;
import java.util.ArrayList;
import java.util.List;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.After;
import org.junit.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static zipkin2.Span.Kind.CONSUMER;

public class TracingRabbitListenerAdviceTest {

  static String TRACE_ID = "463ac35c9f6413ad";
  static String PARENT_ID = "463ac35c9f6413ab";
  static String SPAN_ID = "48485a3953bb6124";
  static String SAMPLED = "1";

  List<Span> spans = new ArrayList<>();
  Tracing tracing = Tracing.newBuilder()
    .currentTraceContext(ThreadLocalCurrentTraceContext.create())
    .spanReporter(spans::add)
    .build();
  TracingRabbitListenerAdvice tracingRabbitListenerAdvice = new TracingRabbitListenerAdvice(
    SpringRabbitTracing.newBuilder(tracing).remoteServiceName("my-exchange").build()
  );
  MethodInvocation methodInvocation = mock(MethodInvocation.class);

  @After public void close() {
    tracing.close();
  }

  @Test public void starts_new_trace_if_none_exists() throws Throwable {
    Message message = MessageBuilder.withBody(new byte[0]).build();
    onMessageConsumed(message);

    assertThat(spans)
      .extracting(Span::kind)
      .containsExactly(CONSUMER, null);
  }

  @Test public void consumer_and_listener_have_names() throws Throwable {
    Message message = MessageBuilder.withBody(new byte[0]).build();
    onMessageConsumed(message);

    assertThat(spans)
      .extracting(Span::name)
      .containsExactly("next-message", "on-message");
  }

  @Test public void consumer_has_remote_service_name() throws Throwable {
    Message message = MessageBuilder.withBody(new byte[0]).build();
    onMessageConsumed(message);

    assertThat(spans)
      .extracting(Span::remoteServiceName)
      .containsExactly("my-exchange", null);
  }

  @Test public void tags_consumer_span_but_not_listener() throws Throwable {
    MessageProperties properties = new MessageProperties();
    properties.setConsumerQueue("foo");

    Message message = MessageBuilder.withBody(new byte[0]).andProperties(properties).build();
    onMessageConsumed(message);

    assertThat(spans.get(0).tags())
      .containsExactly(entry("rabbit.queue", "foo"));
    assertThat(spans.get(1).tags())
      .isEmpty();
  }

  @Test public void consumer_span_starts_before_listener() throws Throwable {
    Message message = MessageBuilder.withBody(new byte[0]).build();
    onMessageConsumed(message);

    // make sure one before the other
    assertThat(spans.get(0).timestampAsLong())
      .isLessThan(spans.get(1).timestampAsLong());

    // make sure they finished
    assertThat(spans.get(0).durationAsLong())
      .isPositive();
    assertThat(spans.get(1).durationAsLong())
      .isPositive();
  }

  @Test public void continues_parent_trace() throws Throwable {
    MessageProperties props = new MessageProperties();
    props.setHeader("X-B3-TraceId", TRACE_ID);
    props.setHeader("X-B3-SpanId", SPAN_ID);
    props.setHeader("X-B3-ParentSpanId", PARENT_ID);
    props.setHeader("X-B3-Sampled", SAMPLED);

    Message message = MessageBuilder.withBody(new byte[0]).andProperties(props).build();
    onMessageConsumed(message);

    // cleared the headers to later work doesn't try to use the old parent
    assertThat(message.getMessageProperties().getHeaders()).isEmpty();

    assertThat(spans)
      .filteredOn(span -> span.kind() == CONSUMER)
      .extracting(Span::parentId)
      .contains(SPAN_ID);
  }

  @Test public void continues_parent_trace_single_header() throws Throwable {
    MessageProperties props = new MessageProperties();
    props.setHeader("b3", TRACE_ID + "-" + SPAN_ID + "-" + SAMPLED);

    Message message = MessageBuilder.withBody(new byte[0]).andProperties(props).build();
    onMessageConsumed(message);

    // cleared the headers to later work doesn't try to use the old parent
    assertThat(message.getMessageProperties().getHeaders()).isEmpty();

    assertThat(spans)
      .filteredOn(span -> span.kind() == CONSUMER)
      .extracting(Span::parentId)
      .contains(SPAN_ID);
  }

  @Test public void reports_span_if_consume_fails() throws Throwable {
    Message message = MessageBuilder.withBody(new byte[0]).build();
    onMessageConsumeFailed(message, new RuntimeException("expected exception"));

    assertThat(spans)
      .extracting(Span::kind)
      .containsExactly(CONSUMER, null);

    assertThat(spans)
      .filteredOn(span -> span.kind() == null)
      .extracting(Span::tags)
      .extracting(tags -> tags.get("error"))
      .contains("expected exception");
  }

  @Test public void reports_span_if_consume_fails_with_no_message() throws Throwable {
    Message message = MessageBuilder.withBody(new byte[0]).build();
    onMessageConsumeFailed(message, new RuntimeException());

    assertThat(spans)
      .extracting(Span::kind)
      .containsExactly(CONSUMER, null);

    assertThat(spans)
      .filteredOn(span -> span.kind() == null)
      .extracting(Span::tags)
      .extracting(tags -> tags.get("error"))
      .contains("RuntimeException");
  }

  void onMessageConsumed(Message message) throws Throwable {
    when(methodInvocation.getArguments()).thenReturn(new Object[] {
      null, // AMQPChannel - doesn't matter
      message
    });
    when(methodInvocation.proceed()).thenReturn("doesn't matter");

    tracingRabbitListenerAdvice.invoke(methodInvocation);
  }

  void onMessageConsumeFailed(Message message, Throwable throwable) throws Throwable {
    when(methodInvocation.getArguments()).thenReturn(new Object[] {
      null, // AMQPChannel - doesn't matter
      message
    });
    when(methodInvocation.proceed()).thenThrow(throwable);

    try {
      tracingRabbitListenerAdvice.invoke(methodInvocation);
      fail("should have thrown exception");
    } catch (RuntimeException ex) {
    }
  }
}
