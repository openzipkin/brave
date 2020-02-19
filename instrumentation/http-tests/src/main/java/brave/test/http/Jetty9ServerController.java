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
package brave.test.http;

import brave.test.http.ServletContainer.ServerController;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

public final class Jetty9ServerController implements ServerController {
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
