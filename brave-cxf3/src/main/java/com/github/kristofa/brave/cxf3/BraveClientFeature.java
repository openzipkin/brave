package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import org.apache.cxf.Bus;
import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.interceptor.InterceptorProvider;

/**
 * Configures cxf client with brave interceptors.
 */
public class BraveClientFeature extends AbstractFeature {
  private final Brave brave;

  public static BraveClientFeature create(Brave brave) {
    return new BraveClientFeature(brave);
  }

  BraveClientFeature(final Brave brave) {
    this.brave = brave;
  }

  @Override
  protected void initializeProvider(InterceptorProvider interceptorProvider, Bus bus) {
    interceptorProvider.getInInterceptors().add(BraveClientInInterceptor.create(brave));
    interceptorProvider.getOutInterceptors().add(BraveClientOutInterceptor.create(brave));
  }
}