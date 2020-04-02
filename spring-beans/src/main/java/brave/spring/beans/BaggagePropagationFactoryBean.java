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
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationCustomizer;
import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import java.util.List;
import org.springframework.beans.factory.FactoryBean;

/** Spring XML config does not support chained builders. This converts accordingly */
public class BaggagePropagationFactoryBean implements FactoryBean {
  Propagation.Factory propagationFactory = B3Propagation.FACTORY;
  List<BaggageField> fields;
  List<RemoteBaggageField> remoteFields;
  List<BaggagePropagationCustomizer> customizers;

  @Override public Propagation.Factory getObject() {
    BaggagePropagation.FactoryBuilder builder =
      BaggagePropagation.newFactoryBuilder(propagationFactory);
    if (fields != null) {
      for (BaggageField field : fields) builder.addField(field);
    }
    if (remoteFields != null) {
      for (RemoteBaggageField remoteField : remoteFields)
        builder.addRemoteField(remoteField.field, remoteField.keyNames);
    }
    if (customizers != null) {
      for (BaggagePropagationCustomizer customizer : customizers) customizer.customize(builder);
    }
    return builder.build();
  }

  @Override public Class<? extends Propagation.Factory> getObjectType() {
    return Propagation.Factory.class;
  }

  @Override public boolean isSingleton() {
    return true;
  }

  public void setPropagationFactory(Propagation.Factory propagationFactory) {
    this.propagationFactory = propagationFactory;
  }

  public void setFields(List<BaggageField> fields) {
    this.fields = fields;
  }

  public void setRemoteFields(List<RemoteBaggageField> remoteFields) {
    this.remoteFields = remoteFields;
  }

  public void setCustomizers(List<BaggagePropagationCustomizer> customizers) {
    this.customizers = customizers;
  }
}
