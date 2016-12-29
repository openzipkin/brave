package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.SpanNameProvider;
import org.apache.cxf.jaxrs.interceptor.JAXRSInInterceptor;
import org.apache.cxf.jaxrs.model.OperationResourceInfo;
import org.apache.cxf.message.Message;

import javax.ws.rs.container.Suspended;
import java.lang.annotation.Annotation;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

public class BraveJaxRsServerInInterceptor extends AbstractBraveServerInInterceptor {

  /**
   * Creates a tracing interceptor with defaults. Use {@link #builder(Brave)} to customize.
   */
  public static BraveJaxRsServerInInterceptor create(Brave brave) {
    return new BraveJaxRsServerInInterceptor.Builder(brave).build();
  }

  public static BraveJaxRsServerInInterceptor.Builder builder(Brave brave) {
    return new BraveJaxRsServerInInterceptor.Builder(brave);
  }

  public static final class Builder {
    final Brave brave;
    SpanNameProvider spanNameProvider = new DefaultSpanNameProvider();

    Builder(Brave brave) { // intentionally hidden
      this.brave = checkNotNull(brave, "brave");
    }

    public BraveJaxRsServerInInterceptor.Builder spanNameProvider(SpanNameProvider spanNameProvider) {
      this.spanNameProvider = checkNotNull(spanNameProvider, "spanNameProvider");
      return this;
    }

    public BraveJaxRsServerInInterceptor build() {
      return new BraveJaxRsServerInInterceptor(this);
    }
  }

  BraveJaxRsServerInInterceptor(Builder b) {
    super(b.brave, b.spanNameProvider);
    addAfter(JAXRSInInterceptor.class.getName());
  }

  @Override
  protected boolean isAsync(Message message) {
    final OperationResourceInfo resource = message.getExchange().get(OperationResourceInfo.class);
    if (resource != null) {
      for (final Annotation[] annotations : resource.getMethodToInvoke().getParameterAnnotations()) {
        for (final Annotation annotation : annotations) {
          if (annotation.annotationType().equals(Suspended.class)) {
            return true;
          }
        }
      }
    }
    return false;
  }
}