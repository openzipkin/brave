/*
 * Copyright 2013-2019 The OpenZipkin Authors
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

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import java.util.List;
import org.springframework.beans.factory.FactoryBean;

/** Spring XML config does not support chained builders. This converts accordingly */
public class CurrentTraceContextFactoryBean implements FactoryBean {

  List<ScopeDecorator> scopeDecorators;

  @Override public CurrentTraceContext getObject() {
    CurrentTraceContext.Builder builder = ThreadLocalCurrentTraceContext.newBuilder();
    if (scopeDecorators != null) {
      for (ScopeDecorator scopeDecorator : scopeDecorators) {
        builder.addScopeDecorator(scopeDecorator);
      }
    }
    return builder.build();
  }

  @Override public Class<? extends CurrentTraceContext> getObjectType() {
    return CurrentTraceContext.class;
  }

  @Override public boolean isSingleton() {
    return true;
  }

  public void setScopeDecorators(List<ScopeDecorator> scopeDecorators) {
    this.scopeDecorators = scopeDecorators;
  }
}
