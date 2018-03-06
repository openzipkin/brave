package brave.spring.rabbit;

import brave.Tracing;
import brave.sampler.Sampler;
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
  SpringRabbitTracing tracing = SpringRabbitTracing.create(Tracing.newBuilder()
      .sampler(Sampler.ALWAYS_SAMPLE)
      .spanReporter(Reporter.NOOP)
      .build());

  @After public void close() {
    Tracing.current().close();
  }

  @Test public void decorateRabbitTemplate_adds_by_default() {
    RabbitTemplate template = new RabbitTemplate();
    assertThat(tracing.decorateRabbitTemplate(template))
        .extracting("beforePublishPostProcessors")
        .containsExactly(asList(tracing.tracingMessagePostProcessor));
  }

  @Test public void decorateRabbitTemplate_skips_when_present() {
    RabbitTemplate template = new RabbitTemplate();
    template.setBeforePublishPostProcessors(tracing.tracingMessagePostProcessor);

    assertThat(tracing.decorateRabbitTemplate(template))
        .extracting("beforePublishPostProcessors")
        .containsExactly(asList(tracing.tracingMessagePostProcessor));
  }

  @Test public void decorateRabbitTemplate_appends_when_absent() {
    RabbitTemplate template = new RabbitTemplate();
    UnzipPostProcessor postProcessor = new UnzipPostProcessor();
    template.setBeforePublishPostProcessors(postProcessor);

    assertThat(tracing.decorateRabbitTemplate(template))
        .extracting("beforePublishPostProcessors")
        .containsExactly(asList(postProcessor, tracing.tracingMessagePostProcessor));
  }

  @Test public void decorateSimpleRabbitListenerContainerFactory_adds_by_default() {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();

    assertThat(tracing.decorateSimpleRabbitListenerContainerFactory(factory).getAdviceChain())
        .containsExactly(tracing.tracingRabbitListenerAdvice);
  }

  @Test public void decorateSimpleRabbitListenerContainerFactory_skips_when_present() {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setAdviceChain(tracing.tracingRabbitListenerAdvice);

    assertThat(tracing.decorateSimpleRabbitListenerContainerFactory(factory).getAdviceChain())
        .containsExactly(tracing.tracingRabbitListenerAdvice);
  }

  @Test public void decorateSimpleRabbitListenerContainerFactory_appends_when_absent() {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    CacheInterceptor advice = new CacheInterceptor();
    factory.setAdviceChain(advice);

    assertThat(tracing.decorateSimpleRabbitListenerContainerFactory(factory).getAdviceChain())
        .containsExactly(advice, tracing.tracingRabbitListenerAdvice);
  }
}
