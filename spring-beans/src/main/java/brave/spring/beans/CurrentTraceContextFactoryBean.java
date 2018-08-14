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
