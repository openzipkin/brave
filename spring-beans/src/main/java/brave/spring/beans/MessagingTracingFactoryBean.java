/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.beans;

import brave.Tracing;
import brave.messaging.MessagingRequest;
import brave.messaging.MessagingTracing;
import brave.messaging.MessagingTracingCustomizer;
import brave.propagation.Propagation;
import brave.sampler.SamplerFunction;
import java.util.List;
import org.springframework.beans.factory.FactoryBean;

/** Spring XML config does not support chained builders. This converts accordingly */
public class MessagingTracingFactoryBean implements FactoryBean {
  Tracing tracing;
  SamplerFunction<MessagingRequest> producerSampler, consumerSampler;
  Propagation<String> propagation;
  List<MessagingTracingCustomizer> customizers;

  @Override public MessagingTracing getObject() {
    MessagingTracing.Builder builder = MessagingTracing.newBuilder(tracing);
    if (producerSampler != null) builder.producerSampler(producerSampler);
    if (consumerSampler != null) builder.consumerSampler(consumerSampler);
    if (propagation != null) builder.propagation(propagation);
    if (customizers != null) {
      for (MessagingTracingCustomizer customizer : customizers) customizer.customize(builder);
    }
    return builder.build();
  }

  @Override public Class<? extends MessagingTracing> getObjectType() {
    return MessagingTracing.class;
  }

  @Override public boolean isSingleton() {
    return true;
  }

  public void setTracing(Tracing tracing) {
    this.tracing = tracing;
  }

  public void setProducerSampler(SamplerFunction<MessagingRequest> producerSampler) {
    this.producerSampler = producerSampler;
  }

  public void setConsumerSampler(SamplerFunction<MessagingRequest> consumerSampler) {
    this.consumerSampler = consumerSampler;
  }

  public void setPropagation(Propagation<String> propagation) {
    this.propagation = propagation;
  }

  public void setCustomizers(List<MessagingTracingCustomizer> customizers) {
    this.customizers = customizers;
  }
}
