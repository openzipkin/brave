/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
import java.util.Collection;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.postprocessor.UnzipPostProcessor;
import org.springframework.cache.interceptor.CacheInterceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.array;
import static org.assertj.core.api.InstanceOfAssertFactories.iterable;

public class SpringRabbitTracingTest {
  Tracing tracing = Tracing.newBuilder().build();
  SpringRabbitTracing rabbitTracing = SpringRabbitTracing.create(tracing);

  @After public void close() {
    tracing.close();
  }

  @Test public void decorateRabbitTemplate_adds_TracingMessagePostProcessor() {
    RabbitTemplate template = new RabbitTemplate();
    assertThat(rabbitTracing.decorateRabbitTemplate(template))
      .extracting("beforePublishPostProcessors")
      .satisfies(postProcessors -> assertThat(((Collection) postProcessors)).anyMatch(
        postProcessor -> postProcessor instanceof TracingMessagePostProcessor
      ));
  }

  @Test public void decorateRabbitTemplate_skips_TracingMessagePostProcessor_when_present() {
    RabbitTemplate template = new RabbitTemplate();
    template.setBeforePublishPostProcessors(new TracingMessagePostProcessor(rabbitTracing));

    assertThat(rabbitTracing.decorateRabbitTemplate(template))
      .extracting("beforePublishPostProcessors")
      .satisfies(l -> assertThat((List<?>) l).hasSize(1));
  }

  @Test public void decorateRabbitTemplate_prepends_TracingMessagePostProcessor_when_absent() {
    RabbitTemplate template = new RabbitTemplate();
    template.setBeforePublishPostProcessors(new UnzipPostProcessor());

    assertThat(rabbitTracing.decorateRabbitTemplate(template))
      .extracting("beforePublishPostProcessors")
      .asInstanceOf(iterable(MessagePostProcessor.class))
      .anyMatch(postProcessor -> postProcessor instanceof TracingMessagePostProcessor);
  }

  @Test public void decorateSimpleRabbitListenerContainerFactory_adds_by_default() {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();

    assertThat(rabbitTracing.decorateSimpleRabbitListenerContainerFactory(factory).getAdviceChain())
      .allMatch(advice -> advice instanceof TracingRabbitListenerAdvice);
  }

  @Test public void decorateSimpleRabbitListenerContainerFactory_skips_when_present() {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setAdviceChain(new TracingRabbitListenerAdvice(rabbitTracing));

    assertThat(rabbitTracing.decorateSimpleRabbitListenerContainerFactory(factory).getAdviceChain())
      .hasSize(1);
  }

  @Test public void decorateSimpleRabbitListenerContainerFactory_prepends_as_first_when_absent() {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setAdviceChain(new CacheInterceptor());

    // the order of advices is important for the downstream interceptor to see the tracing context
    assertThat(rabbitTracing.decorateSimpleRabbitListenerContainerFactory(factory).getAdviceChain())
      .hasSize(2)
      .matches(adviceArray -> adviceArray[0] instanceof TracingRabbitListenerAdvice);
  }

  @Test
  public void decorateSimpleRabbitListenerContainerFactory_adds_TracingMessagePostProcessor() {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    assertThat(rabbitTracing.decorateSimpleRabbitListenerContainerFactory(factory))
      .extracting("beforeSendReplyPostProcessors")
      .asInstanceOf(array(MessagePostProcessor[].class))
      .allMatch(postProcessor -> postProcessor instanceof TracingMessagePostProcessor);
  }

  @Test
  public void decorateSimpleRabbitListenerContainerFactory_skips_TracingMessagePostProcessor_when_present() {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setBeforeSendReplyPostProcessors(new TracingMessagePostProcessor(rabbitTracing));

    assertThat(rabbitTracing.decorateSimpleRabbitListenerContainerFactory(factory))
      .extracting("beforeSendReplyPostProcessors")
      .asInstanceOf(array(MessagePostProcessor[].class))
      .hasSize(1);
  }

  @Test
  public void decorateSimpleRabbitListenerContainerFactory_appends_TracingMessagePostProcessor_when_absent() {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setBeforeSendReplyPostProcessors(new UnzipPostProcessor());

    assertThat(rabbitTracing.decorateSimpleRabbitListenerContainerFactory(factory))
      .extracting("beforeSendReplyPostProcessors")
      .asInstanceOf(array(MessagePostProcessor[].class))
      .matches(adviceArray -> adviceArray[1] instanceof TracingMessagePostProcessor);
  }
}
