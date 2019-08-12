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
package brave.jaxrs2;

import brave.http.HttpServerBenchmarks;
import brave.propagation.ExtraFieldPropagation;
import io.undertow.Undertow;
import io.undertow.servlet.api.DeploymentInfo;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Set;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import static brave.servlet.ServletBenchmarks.addFilterMappings;

public class JaxRs2ServerBenchmarks extends HttpServerBenchmarks {

  @Produces("text/plain; charset=UTF-8")
  public static class Resource {
    @GET @Path("/nottraced")
    public String nottraced() {
      return "hello world";
    }

    @GET @Path("/unsampled")
    public String unsampled() {
      return "hello world";
    }

    @GET @Path("/traced")
    public String traced() {
      return "hello world";
    }

    @GET @Path("/tracedextra")
    public String tracedextra() {
      // noop if not configured
      ExtraFieldPropagation.set("country-code", "FO");
      return "hello world";
    }

    @GET @Path("/traced128")
    public String traced128() {
      return "hello world";
    }
  }

  @ApplicationPath("/")
  public static class App extends Application {
    @Override public Set<Object> getSingletons() {
      return Collections.singleton(new Resource());
    }
  }

  PortExposing server;

  @Override protected int initServer() {
    server = (PortExposing) new PortExposing()
      .deploy(App.class)
      .start(Undertow.builder().addHttpListener(8888, "127.0.0.1"));
    return server.getPort();
  }

  static class PortExposing extends UndertowJaxrsServer {
    int getPort() {
      return ((InetSocketAddress) server.getListenerInfo().get(0).getAddress()).getPort();
    }
  }

  @Override protected void init(DeploymentInfo servletBuilder) {
    addFilterMappings(servletBuilder);
  }

  @TearDown(Level.Trial) @Override public void close() throws Exception {
    server.stop();
    super.close();
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(".*" + JaxRs2ServerBenchmarks.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}
