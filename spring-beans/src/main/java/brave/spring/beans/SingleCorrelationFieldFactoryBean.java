/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.beans;

import brave.baggage.BaggageField;
import brave.baggage.CorrelationScopeConfig;
import brave.baggage.CorrelationScopeConfig.SingleCorrelationField;
import org.springframework.beans.factory.FactoryBean;

/** Spring XML config does not support chained builders. This converts accordingly */
public class SingleCorrelationFieldFactoryBean implements FactoryBean {
  BaggageField baggageField;
  String name;
  boolean dirty, flushOnUpdate;

  @Override public SingleCorrelationField getObject() {
    SingleCorrelationField.Builder builder = SingleCorrelationField.newBuilder(baggageField);
    if (name != null) builder.name(name);
    if (dirty) builder.dirty();
    if (flushOnUpdate) builder.flushOnUpdate();
    return builder.build();
  }

  @Override public Class<? extends CorrelationScopeConfig> getObjectType() {
    return CorrelationScopeConfig.class;
  }

  @Override public boolean isSingleton() {
    return true;
  }

  public void setBaggageField(BaggageField baggageField) {
    this.baggageField = baggageField;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setDirty(boolean dirty) {
    this.dirty = dirty;
  }

  public void setFlushOnUpdate(boolean flushOnUpdate) {
    this.flushOnUpdate = flushOnUpdate;
  }
}
