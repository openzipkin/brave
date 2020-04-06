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
