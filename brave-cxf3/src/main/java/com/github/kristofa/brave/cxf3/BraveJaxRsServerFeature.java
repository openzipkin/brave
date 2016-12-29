package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.jaxrs.provider.ServerProviderFactory;

import java.util.Arrays;

/**
 * Configures cxf jax-rs server with brave interceptors.
 */
public class BraveJaxRsServerFeature extends AbstractFeature {
  protected final Brave brave;

  public static BraveJaxRsServerFeature create(Brave brave) {
    return new BraveJaxRsServerFeature(brave);
  }

  BraveJaxRsServerFeature(final Brave brave) {
    this.brave = brave;
  }

  @Override
  public void initialize(Server server, Bus bus) {
    server.getEndpoint().getInInterceptors().add(BraveJaxRsServerInInterceptor.create(brave));
    server.getEndpoint().getOutInterceptors().add(BraveServerOutInterceptor.create(brave));
    server.getEndpoint().getOutFaultInterceptors().add(BraveServerOutInterceptor.create(brave));

    final ServerProviderFactory providerFactory = (ServerProviderFactory) server.getEndpoint().get(ServerProviderFactory.class.getName());
    if (providerFactory != null) {
      providerFactory.setUserProviders(Arrays.asList(new TracerContextProvider(brave)));
    }
  }
}