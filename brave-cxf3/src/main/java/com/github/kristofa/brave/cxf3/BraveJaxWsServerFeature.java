package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.feature.AbstractFeature;

/**
 * Configures cxf jax-ws server with brave interceptors.
 */
public class BraveJaxWsServerFeature extends AbstractFeature {
  protected final Brave brave;

  public static BraveJaxWsServerFeature create(Brave brave) {
    return new BraveJaxWsServerFeature(brave);
  }

  BraveJaxWsServerFeature(final Brave brave) {
    this.brave = brave;
  }

  @Override
  public void initialize(Server server, Bus bus) {
    server.getEndpoint().getInInterceptors().add(BraveJaxWsServerInInterceptor.create(brave));
    server.getEndpoint().getOutInterceptors().add(BraveServerOutInterceptor.create(brave));
    server.getEndpoint().getOutFaultInterceptors().add(BraveServerOutInterceptor.create(brave));
  }
}