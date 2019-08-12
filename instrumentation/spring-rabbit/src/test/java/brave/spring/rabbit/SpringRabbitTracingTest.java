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
import brave.propagation.Propagation;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.Collection;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.postprocessor.UnzipPostProcessor;
import org.springframework.cache.interceptor.CacheInterceptor;
import zipkin2.reporter.Reporter;

import static brave.spring.rabbit.SpringRabbitPropagation.GETTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SpringRabbitTracingTest {
  Tracing tracing = Tracing.newBuilder()
    .currentTraceContext(ThreadLocalCurrentTraceContext.create())
    .spanReporter(Reporter.NOOP)
    .build();
  SpringRabbitTracing rabbitTracing = SpringRabbitTracing.create(tracing);

  @After public void close() {
    Tracing.current().close();
  }

  @Test public void decorateRabbitTemplate_adds_by_default() {
    RabbitTemplate template = new RabbitTemplate();
    assertThat(rabbitTracing.decorateRabbitTemplate(template))
      .extracting("beforePublishPostProcessors")
      .satisfies(postProcessors -> assertThat(((Collection) postProcessors)).anyMatch(
        postProcessor -> postProcessor instanceof TracingMessagePostProcessor
      ));
  }

  @Test public void decorateRabbitTemplate_skips_when_present() {
    RabbitTemplate template = new RabbitTemplate();
    template.setBeforePublishPostProcessors(new TracingMessagePostProcessor(rabbitTracing));

    assertThat(rabbitTracing.decorateRabbitTemplate(template))
      .extracting("beforePublishPostProcessors")
      .satisfies(l -> assertThat((List<?>) l).hasSize(1));
  }

  @Test public void decorateRabbitTemplate_appends_when_absent() {
    RabbitTemplate template = new RabbitTemplate();
    template.setBeforePublishPostProcessors(new UnzipPostProcessor());

    assertThat(rabbitTracing.decorateRabbitTemplate(template))
      .extracting("beforePublishPostProcessors")
      .satisfies(postProcessors -> assertThat(((Collection) postProcessors)).anyMatch(
        postProcessor -> postProcessor instanceof TracingMessagePostProcessor
      ));
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

  @Test public void decorateSimpleRabbitListenerContainerFactory_appends_when_absent() {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setAdviceChain(new CacheInterceptor());

    assertThat(rabbitTracing.decorateSimpleRabbitListenerContainerFactory(factory).getAdviceChain())
      .anyMatch(advice -> advice instanceof TracingRabbitListenerAdvice);
  }

  @Test public void failsFastIfPropagationDoesntSupportSingleHeader() {
    // Fake propagation because B3 by default does support single header extraction!
    Propagation<String> propagation = mock(Propagation.class);
    when(propagation.extractor(GETTER)).thenReturn(carrier -> {
      assertThat(carrier.getHeaders().get("b3")).isNotNull(); // sanity check
      return TraceContextOrSamplingFlags.EMPTY; // pretend we couldn't parse
    });

    Propagation.Factory propagationFactory = mock(Propagation.Factory.class);
    when(propagationFactory.create(Propagation.KeyFactory.STRING)).thenReturn(propagation);

    assertThatThrownBy(() -> SpringRabbitTracing.newBuilder(
      Tracing.newBuilder().propagationFactory(propagationFactory).build())
      .writeB3SingleFormat(true)
      .build()
    ).hasMessage(
      "SpringRabbitTracing.Builder.writeB3SingleFormat set, but Tracing.Builder.propagationFactory cannot parse this format!");
  }
}
