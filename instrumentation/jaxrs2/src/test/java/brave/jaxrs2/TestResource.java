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
package brave.jaxrs2;

import brave.Tracer;
import brave.http.HttpTracing;
import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;

import static brave.test.ITRemote.BAGGAGE_FIELD;
import static brave.test.http.ITHttpServer.NOT_READY_ISE;

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
    return Response.ok(BAGGAGE_FIELD.getValue()).build();
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
  public void async(@Suspended AsyncResponse response) {
    new Thread(() -> response.resume(Response.status(200).build())).start();
  }

  @GET
  @Path("exception")
  public Response notReady() {
    throw new WebApplicationException(NOT_READY_ISE, 503);
  }

  @GET
  @Path("exceptionAsync")
  public void notReadyAsync(@Suspended AsyncResponse response) {
    new Thread(() -> response.resume(
      new WebApplicationException(NOT_READY_ISE, 503)
    )).start();
  }
}
