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
import org.springframework.beans.factory.FactoryBean;

/** Spring XML config does not support chained builders. This converts accordingly */
public class BaggageFieldFactoryBean implements FactoryBean {
  String name;
  boolean flushOnUpdate;

  @Override public BaggageField getObject() {
    BaggageField.Builder builder = BaggageField.newBuilder(name);
    if (flushOnUpdate) builder.flushOnUpdate();
    return builder.build();
  }

  @Override public Class<? extends BaggageField> getObjectType() {
    return BaggageField.class;
  }

  @Override public boolean isSingleton() {
    return true;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setFlushOnUpdate(boolean flushOnUpdate) {
    this.flushOnUpdate = flushOnUpdate;
  }
}
