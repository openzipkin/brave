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
import brave.baggage.BaggagePropagationConfig;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.FactoryBean;

/** Spring XML config does not support chained builders. This converts accordingly */
public class SingleBaggageFieldFactoryBean implements FactoryBean {
  BaggageField field;
  List<String> keyNames = Collections.emptyList();

  @Override public SingleBaggageField getObject() {
    SingleBaggageField.Builder builder = SingleBaggageField.newBuilder(field);
    if (keyNames != null) {
      for (String keyName : keyNames) {
        builder.addKeyName(keyName);
      }
    }
    return builder.build();
  }

  @Override public Class<? extends BaggagePropagationConfig> getObjectType() {
    return BaggagePropagationConfig.class;
  }

  @Override public boolean isSingleton() {
    return true;
  }

  public void setField(BaggageField field) {
    this.field = field;
  }

  public void setKeyNames(List<String> keyNames) {
    if (keyNames == null) return;
    this.keyNames = keyNames;
  }
}
