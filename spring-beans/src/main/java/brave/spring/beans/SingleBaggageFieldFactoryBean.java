/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
