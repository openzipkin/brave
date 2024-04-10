/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.test.jakarta.http;

import brave.test.http.ServletContainer.ServerController;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

public final class Jetty11ServerController implements ServerController {
  @Override public Server newServer(int port) {
    Server result = new Server();
    ServerConnector connector = new ServerConnector(result);
    connector.setPort(port);
    connector.setIdleTimeout(1000 * 60 * 60);
    result.setConnectors(new Connector[] {connector});
    return result;
  }

  @Override public int getLocalPort(Server server) {
    return ((ServerConnector) server.getConnectors()[0]).getLocalPort();
  }
}
