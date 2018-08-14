package brave.spring.rabbit;

import brave.Tracing;
import brave.propagation.ThreadLocalCurrentTraceContext;
import org.junit.After;
import org.junit.Test;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.postprocessor.UnzipPostProcessor;
import org.springframework.cache.interceptor.CacheInterceptor;
import zipkin2.reporter.Reporter;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Java6Assertions.assertThat;

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
        .containsExactly(asList(rabbitTracing.tracingMessagePostProcessor));
  }

  @Test public void decorateRabbitTemplate_skips_when_present() {
    RabbitTemplate template = new RabbitTemplate();
    template.setBeforePublishPostProcessors(rabbitTracing.tracingMessagePostProcessor);

    assertThat(rabbitTracing.decorateRabbitTemplate(template))
        .extracting("beforePublishPostProcessors")
        .containsExactly(asList(rabbitTracing.tracingMessagePostProcessor));
  }

  @Test public void decorateRabbitTemplate_appends_when_absent() {
    RabbitTemplate template = new RabbitTemplate();
    UnzipPostProcessor postProcessor = new UnzipPostProcessor();
    template.setBeforePublishPostProcessors(postProcessor);

    assertThat(rabbitTracing.decorateRabbitTemplate(template))
        .extracting("beforePublishPostProcessors")
        .containsExactly(asList(postProcessor, rabbitTracing.tracingMessagePostProcessor));
  }

  @Test public void decorateSimpleRabbitListenerContainerFactory_adds_by_default() {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();

    assertThat(rabbitTracing.decorateSimpleRabbitListenerContainerFactory(factory).getAdviceChain())
        .containsExactly(rabbitTracing.tracingRabbitListenerAdvice);
  }

  @Test public void decorateSimpleRabbitListenerContainerFactory_skips_when_present() {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setAdviceChain(rabbitTracing.tracingRabbitListenerAdvice);

    assertThat(rabbitTracing.decorateSimpleRabbitListenerContainerFactory(factory).getAdviceChain())
        .containsExactly(rabbitTracing.tracingRabbitListenerAdvice);
  }

  @Test public void decorateSimpleRabbitListenerContainerFactory_appends_when_absent() {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    CacheInterceptor advice = new CacheInterceptor();
    factory.setAdviceChain(advice);

    assertThat(rabbitTracing.decorateSimpleRabbitListenerContainerFactory(factory).getAdviceChain())
        .containsExactly(advice, rabbitTracing.tracingRabbitListenerAdvice);
  }
}
