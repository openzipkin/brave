package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import org.apache.cxf.Bus;
import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.interceptor.InterceptorProvider;

/**
 * Configures cxf server with brave interceptors.
 */
public class BraveServerFeature extends AbstractFeature {
  protected final Brave brave;

  public static BraveServerFeature create(Brave brave) {
    return new BraveServerFeature(brave);
  }

  BraveServerFeature(final Brave brave) {
    this.brave = brave;
  }

  @Override
  protected void initializeProvider(InterceptorProvider interceptorProvider, Bus bus) {
    interceptorProvider.getInInterceptors().add(BraveServerInInterceptor.create(brave));
    interceptorProvider.getOutInterceptors().add(BraveServerOutInterceptor.create(brave));
  }
}