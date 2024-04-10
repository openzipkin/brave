/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.beans;

import brave.baggage.CorrelationScopeConfig;
import brave.baggage.CorrelationScopeCustomizer;
import brave.baggage.CorrelationScopeDecorator;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import java.util.List;
import org.springframework.beans.factory.FactoryBean;

/** Spring XML config does not support chained builders. This converts accordingly */
public class CorrelationScopeDecoratorFactoryBean implements FactoryBean {
  CorrelationScopeDecorator.Builder builder;
  List<CorrelationScopeConfig> configs;
  List<CorrelationScopeCustomizer> customizers;

  @Override public ScopeDecorator getObject() {
    if (builder == null) throw new NullPointerException("builder == null");
    if (configs != null) {
      builder.clear();
      for (CorrelationScopeConfig config : configs) {
        builder.add(config);
      }
    }
    if (customizers != null) {
      for (CorrelationScopeCustomizer customizer : customizers) customizer.customize(builder);
    }
    return builder.build();
  }

  @Override public Class<? extends ScopeDecorator> getObjectType() {
    return ScopeDecorator.class;
  }

  @Override public boolean isSingleton() {
    return true;
  }

  public void setBuilder(CorrelationScopeDecorator.Builder builder) {
    this.builder = builder;
  }

  public void setConfigs(List<CorrelationScopeConfig> configs) {
    this.configs = configs;
  }

  public void setCustomizers(List<CorrelationScopeCustomizer> customizers) {
    this.customizers = customizers;
  }
}
