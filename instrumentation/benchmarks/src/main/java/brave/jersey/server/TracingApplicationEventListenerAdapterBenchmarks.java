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
package brave.jersey.server;

import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.internal.monitoring.RequestEventImpl;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.uri.PathTemplate;
import org.jboss.resteasy.core.ServerResponse;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class TracingApplicationEventListenerAdapterBenchmarks {
  FakeExtendedUriInfo uriInfo = new FakeExtendedUriInfo(URI.create("/"),
    Arrays.asList(
      new PathTemplate("/"),
      new PathTemplate("/items/{itemId}")
    )
  );
  ContainerRequest request = new ContainerRequest(
    URI.create("/"), null, null, null, new MapPropertiesDelegate()
  ) {
    @Override public ExtendedUriInfo getUriInfo() {
      return uriInfo;
    }
  };
  ContainerResponse response = new ContainerResponse(request, new ServerResponse());
  RequestEvent event = new RequestEventImpl.Builder()
    .setContainerRequest(request)
    .setContainerResponse(response).build(RequestEvent.Type.FINISHED);

  FakeExtendedUriInfo nestedUriInfo = new FakeExtendedUriInfo(URI.create("/"),
    Arrays.asList(
      new PathTemplate("/"),
      new PathTemplate("/items/{itemId}"),
      new PathTemplate("/"),
      new PathTemplate("/nested")
    )
  );

  TracingApplicationEventListener.Adapter adapter = new TracingApplicationEventListener.Adapter();

  @Benchmark public String parseRoute() {
    return adapter.route(event);
  }

  @Benchmark public String parseRoute_nested() {
    return adapter.route(event);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(".*" + TracingApplicationEventListenerAdapterBenchmarks.class.getSimpleName())
      .build();

    new Runner(opt).run();
  }
}
