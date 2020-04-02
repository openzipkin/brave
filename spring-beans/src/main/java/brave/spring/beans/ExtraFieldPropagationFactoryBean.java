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

import brave.propagation.B3Propagation;
import brave.propagation.ExtraFieldCustomizer;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.Propagation;
import java.util.List;
import org.springframework.beans.factory.FactoryBean;

/** @deprecated Since 5.11 use {@link BaggageFieldFactoryBean} */
@Deprecated public class ExtraFieldPropagationFactoryBean implements FactoryBean {
  Propagation.Factory propagationFactory = B3Propagation.FACTORY;
  List<String> fields;
  List<ExtraFieldCustomizer> customizers;

  @Override public Propagation.Factory getObject() {
    ExtraFieldPropagation.FactoryBuilder builder =
      ExtraFieldPropagation.newFactoryBuilder(propagationFactory);
    if (fields != null) {
      for (String field : fields) builder.addField(field);
    }
    if (customizers != null) {
      for (ExtraFieldCustomizer customizer : customizers) customizer.customize(builder);
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

  public void setFields(List<String> fields) {
    this.fields = fields;
  }

  public void setCustomizers(List<ExtraFieldCustomizer> customizers) {
    this.customizers = customizers;
  }
}
