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

import brave.Tracer;
import brave.http.HttpTracing;
import brave.propagation.ExtraFieldPropagation;
import java.io.IOException;
import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.Path;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;

import static brave.test.http.ITHttp.EXTRA_KEY;

@Path("")
public class TestResource { // public for resteasy to inject
  final Tracer tracer;

  TestResource(HttpTracing httpTracing) {
    this.tracer = httpTracing.tracing().tracer();
  }

  @OPTIONS
  @Path("")
  public Response root() {
    return Response.ok().build();
  }

  @GET
  @Path("foo")
  public Response foo() {
    return Response.ok().build();
  }

  @GET
  @Path("extra")
  public Response extra() {
    return Response.ok(ExtraFieldPropagation.get(EXTRA_KEY)).build();
  }

  @GET
  @Path("badrequest")
  public Response badrequest() {
    return Response.status(400).build();
  }

  @GET
  @Path("child")
  public Response child() {
    tracer.nextSpan().name("child").start().finish();
    return Response.status(200).build();
  }

  @GET
  @Path("async")
  public void async(@Suspended AsyncResponse response) throws IOException {
    new Thread(() -> response.resume(Response.status(200).build())).start();
  }

  @GET
  @Path("exception")
  public Response disconnect() throws IOException {
    throw new IOException();
  }

  @GET
  @Path("exceptionAsync")
  public void disconnectAsync(@Suspended AsyncResponse response) throws IOException {
    new Thread(() -> response.resume(new IOException())).start();
  }
}
