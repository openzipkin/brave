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

import brave.Tracer;
import brave.http.HttpTracing;
import brave.propagation.ExtraFieldPropagation;
import java.io.IOException;
import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.server.ManagedAsync;

import static brave.test.http.ITHttp.EXTRA_KEY;

@Path("")
public class TestResource {
  final Tracer tracer;

  TestResource(HttpTracing httpTracing) {
    this.tracer = httpTracing.tracing().tracer();
  }

  @OPTIONS // intentionally leave out the @Path annotation
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
  public void async(@Suspended AsyncResponse response) {
    if (tracer.currentSpan() == null) {
      response.resume(new IllegalStateException("couldn't read current span!"));
      return;
    }
    blockOnAsyncResult("foo", response);
  }

  @GET
  @Path("managedAsync")
  @ManagedAsync
  public void managedAsync(@Suspended AsyncResponse response) {
    if (tracer.currentSpan() == null) {
      response.resume(new IllegalStateException("couldn't read current span!"));
      return;
    }
    response.resume("foo");
  }

  @GET
  @Path("exception")
  public Response disconnect() throws IOException {
    throw new IOException();
  }

  @GET
  @Path("items/{itemId}")
  public String item(@PathParam("itemId") String itemId) {
    return itemId;
  }

  @GET
  @Path("async_items/{itemId}")
  public void asyncItem(@PathParam("itemId") String itemId, @Suspended AsyncResponse response) {
    blockOnAsyncResult(itemId, response);
  }

  static void blockOnAsyncResult(String body, AsyncResponse response) {
    Thread thread = new Thread(() -> response.resume(body));
    thread.start();
    try {
      thread.join();
    } catch (InterruptedException e) {
    }
  }

  @GET
  @Path("exceptionAsync")
  public void disconnectAsync(@Suspended AsyncResponse response) {
    Thread thread = new Thread(() -> response.resume(new IOException()));
    thread.start();
    try {
      thread.join();
    } catch (InterruptedException e) {
    }
  }

  public static class NestedResource {
    @GET
    @Path("items/{itemId}")
    public String item(@PathParam("itemId") String itemId) {
      return itemId;
    }
  }

  @Path("nested")
  public NestedResource nestedResource() {
    return new NestedResource();
  }
}
