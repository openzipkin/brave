package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.provider.ServerProviderFactory;

import java.util.Arrays;

/*
 * Configures cxf jax-rs server with brave interceptors.
 */
public class BraveJaxRsServerFeature extends BraveServerFeature {

  public static BraveJaxRsServerFeature create(Brave brave) {
    return new BraveJaxRsServerFeature(brave);
  }

  BraveJaxRsServerFeature(Brave brave) {
    super(brave);
  }

  @Override
  public void initialize(Server server, Bus bus) {

    initialize(server.getEndpoint(), bus);

    final ServerProviderFactory providerFactory = (ServerProviderFactory) server.getEndpoint().get(ServerProviderFactory.class.getName());
    if (providerFactory != null) {
      providerFactory.setUserProviders(Arrays.asList(new BraveAsyncJaxRsThreadCleaner(brave)));
    }
  }
}
